package com.nexorix.whatsapp;

import com.nexorix.ai.AiException;
import com.nexorix.user.ProcessedWebhookEvent;
import com.nexorix.user.ProcessedWebhookEventRepository;
import com.nexorix.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Procesa los mensajes que la persona manda al WhatsApp de Nexorix
 * (el webhook ya paso la verificacion de firma):
 *
 *  1. Codigo de verificacion -> conecta su numero.
 *  2. Nota de voz o texto -> responde la pregunta de su compra: la IA
 *     transcribe y llena las columnas de su tabla personalizada.
 */
@Service
public class WhatsappInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInboundService.class);

    /** Una pregunta sin responder vale por 7 dias. */
    static final int OPEN_QUESTION_DAYS = 7;

    private final WhatsappLinkRepository linkRepository;
    private final PurchaseNoteRepository noteRepository;
    private final ProcessedWebhookEventRepository processedRepository;
    private final PurchaseNoteService noteService;
    private final PurchaseAnswerReader reader;
    private final WhatsappClient whatsapp;
    private final TransactionTemplate tx;
    private final TaskExecutor executor;

    /** Opcional (no existe en algunas pruebas): completa la descripcion del gasto con la respuesta. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.nexorix.transaction.TransactionEnricher enricher;

    public WhatsappInboundService(
            WhatsappLinkRepository linkRepository,
            PurchaseNoteRepository noteRepository,
            ProcessedWebhookEventRepository processedRepository,
            PurchaseNoteService noteService,
            PurchaseAnswerReader reader,
            WhatsappClient whatsapp,
            TransactionTemplate tx,
            @Qualifier("whatsappExecutor") TaskExecutor executor
    ) {
        this.linkRepository = linkRepository;
        this.noteRepository = noteRepository;
        this.processedRepository = processedRepository;
        this.noteService = noteService;
        this.reader = reader;
        this.whatsapp = whatsapp;
        this.tx = tx;
        this.executor = executor;
    }

    /** Un mensaje que llego, ya sacado del JSON de Meta. */
    public record Incoming(String id, String from, String type, String text,
                           String mediaId, String mimeType, String replyToId) {
    }

    /** Lo que se hizo con un mensaje (para registro y pruebas). */
    public enum Outcome {
        VERIFIED, ANSWERED, NO_OPEN_QUESTION, NOT_UNDERSTOOD, UNKNOWN_PHONE, UNSUPPORTED, DUPLICATE
    }

    // ============================================================
    // ENTRADA DEL WEBHOOK
    // ============================================================

    /** Registra cada mensaje (para ignorar reintentos de Meta) y lo procesa en otro hilo. Devuelve cuantos son nuevos. */
    public int receive(JsonNode payload) {
        int accepted = 0;
        for (Incoming message : parse(payload)) {
            if (markProcessed(message)) {
                accepted++;
                executor.execute(() -> {
                    try {
                        Outcome outcome = handle(message);
                        log.info("WhatsApp {} -> {}", message.type(), outcome);
                    } catch (Exception exception) {
                        log.error("Error procesando un mensaje de WhatsApp", exception);
                    }
                });
            }
        }
        return accepted;
    }

    /** Saca los mensajes del JSON. Los avisos de estado (entregado, leido) se ignoran. */
    static List<Incoming> parse(JsonNode payload) {
        List<Incoming> result = new ArrayList<>();
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    String type = message.path("type").asText("");
                    JsonNode media = message.path(type);
                    String text = switch (type) {
                        case "text" -> message.path("text").path("body").asText("");
                        case "button" -> message.path("button").path("text").asText("");
                        default -> "";
                    };
                    result.add(new Incoming(
                            message.path("id").asText(""),
                            PurchaseNoteService.digits(message.path("from").asText("")),
                            type,
                            text,
                            type.equals("audio") ? media.path("id").asText("") : "",
                            type.equals("audio") ? media.path("mime_type").asText("audio/ogg") : "",
                            message.path("context").path("id").asText("")));
                }
            }
        }
        return result;
    }

    private boolean markProcessed(Incoming message) {
        if (message.id().isEmpty() || message.from().isEmpty()) {
            return false;
        }
        try {
            Boolean saved = tx.execute(status -> {
                if (processedRepository.existsByEventId(message.id())) {
                    return false;
                }
                processedRepository.saveAndFlush(new ProcessedWebhookEvent(
                        message.id(), "WHATSAPP", message.type(), null, "RECEIVED"));
                return true;
            });
            return Boolean.TRUE.equals(saved);
        } catch (DataIntegrityViolationException exception) {
            return false; // la misma entrega llego dos veces al mismo tiempo
        }
    }

    // ============================================================
    // PROCESAR UN MENSAJE
    // ============================================================

    public Outcome handle(Incoming message) {
        Optional<WhatsappLink> found = tx.execute(status -> linkRepository.findByPhone(message.from())
                .map(link -> {
                    link.getUser().getName(); // cargarlo dentro de la transaccion
                    return link;
                }));
        if (found == null || found.isEmpty()) {
            // Numero que no esta en Nexorix: no se responde nada.
            return Outcome.UNKNOWN_PHONE;
        }
        WhatsappLink link = found.get();

        if (!link.isVerified()) {
            return tryVerify(link, message);
        }

        if (!message.type().equals("audio") && message.text().isBlank()) {
            reply(message, "Por ahora entiendo notas de voz 🎙️ y mensajes de texto.");
            return Outcome.UNSUPPORTED;
        }

        Long userId = link.getUser().getId();
        PurchaseNote note = tx.execute(status -> openQuestion(userId, message.replyToId()).orElse(null));
        if (note == null) {
            reply(message, "No tengo compras pendientes por preguntarte 🙂. Cuando registres un gasto en Nexorix te escribo.");
            return Outcome.NO_OPEN_QUESTION;
        }

        List<PurchaseColumn> columns = tx.execute(status -> noteService.columnsOf(link.getUser()));
        PurchaseAnswerReader.Question question = new PurchaseAnswerReader.Question(
                note.getDescription(), PurchaseQuestionNotifier.money(note.getAmount()), columns);

        PurchaseAnswerReader.Answer answer;
        String answerType = message.type().equals("audio") ? "VOZ" : "TEXTO";
        try {
            answer = understand(message, question);
        } catch (WhatsappException | AiException exception) {
            log.warn("No se entendio la respuesta de la compra {}: {}", note.getId(), exception.getMessage());
            tx.executeWithoutResult(status -> noteRepository.findById(note.getId())
                    .ifPresent(saved -> saved.answerFailed(exception.getMessage())));
            reply(message, "😕 No pude entender tu respuesta (" + exception.getMessage()
                    + "). ¿Me la mandas otra vez?");
            return Outcome.NOT_UNDERSTOOD;
        }

        String valuesJson = noteService.writeValues(answer.values());
        tx.executeWithoutResult(status -> noteRepository.findById(note.getId())
                .ifPresent(saved -> saved.answered(answerType, answer.transcript(), valuesJson)));

        if (enricher != null) {
            enricher.enrich(note.getTransactionId(), answer.values().get("producto"));
        }

        reply(message, summary(columns, answer.values()));
        return Outcome.ANSWERED;
    }

    private PurchaseAnswerReader.Answer understand(Incoming message, PurchaseAnswerReader.Question question) {
        if (message.type().equals("audio")) {
            if (!reader.isEnabled()) {
                throw new AiException("la IA no está configurada para escuchar audios; escríbeme la respuesta");
            }
            WhatsappClient.Media media = whatsapp.downloadMedia(message.mediaId());
            return reader.readVoice(question, media.bytes(), message.mimeType().isEmpty() ? media.mimeType() : message.mimeType());
        }
        if (!reader.isEnabled()) {
            // Sin IA, el texto se guarda tal cual en la primera columna.
            String first = question.columns().isEmpty() ? null : question.columns().get(0).getKey();
            return new PurchaseAnswerReader.Answer(message.text().trim(),
                    first == null ? Map.of() : Map.of(first, message.text().trim()));
        }
        return reader.readText(question, message.text());
    }

    /** La pregunta citada; si no cito ninguna, la mas reciente sin responder. */
    private Optional<PurchaseNote> openQuestion(Long userId, String replyToId) {
        if (replyToId != null && !replyToId.isEmpty()) {
            Optional<PurchaseNote> quoted = noteRepository.findByQuestionMessageIdAndUserId(replyToId, userId);
            if (quoted.isPresent()) {
                return quoted;
            }
        }
        return noteRepository.findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, PurchaseNoteStatus.PREGUNTADA, LocalDateTime.now().minusDays(OPEN_QUESTION_DAYS));
    }

    private Outcome tryVerify(WhatsappLink link, Incoming message) {
        String code = link.getVerificationCode();
        boolean valid = code != null && link.getCodeExpiresAt() != null
                && link.getCodeExpiresAt().isAfter(LocalDateTime.now())
                && PurchaseNoteService.digits(message.text()).contains(code);
        if (!valid) {
            return Outcome.UNKNOWN_PHONE;
        }
        tx.executeWithoutResult(status -> linkRepository.findById(link.getId()).ifPresent(WhatsappLink::markVerified));
        User user = link.getUser();
        reply(message, "✅ Listo " + firstName(user) + ", tu WhatsApp quedó conectado a Nexorix.\n"
                + "Cada vez que registres una compra te preguntaré qué compraste y podrás responderme con una nota de voz 🎙️");
        return Outcome.VERIFIED;
    }

    static String summary(List<PurchaseColumn> columns, Map<String, String> values) {
        StringBuilder text = new StringBuilder("✅ Guardado en tu tabla de compras:");
        boolean any = false;
        for (PurchaseColumn column : columns) {
            String value = values.get(column.getKey());
            if (value != null && !value.isBlank()) {
                text.append("\n• ").append(column.getLabel()).append(": ").append(value);
                any = true;
            }
        }
        if (!any) {
            text.append("\n(Guardé lo que dijiste; puedes completar las columnas en Nexorix.)");
        }
        return text.toString();
    }

    private void reply(Incoming message, String text) {
        try {
            whatsapp.sendText(message.from(), text);
        } catch (WhatsappException exception) {
            log.warn("No se pudo responder por WhatsApp: {}", exception.getMessage());
        }
    }

    private static String firstName(User user) {
        String name = user.getName() == null ? "" : user.getName().trim();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }
}
