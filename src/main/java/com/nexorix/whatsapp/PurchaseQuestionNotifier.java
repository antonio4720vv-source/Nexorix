package com.nexorix.whatsapp;

import com.nexorix.transaction.PurchaseRegisteredEvent;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Cuando la persona registra una compra, le pregunta por WhatsApp que compro.
 *
 * Corre DESPUES de que el gasto quedo guardado (si el gasto falla, no se
 * pregunta nada) y en otro hilo (la persona no espera a WhatsApp).
 */
@Component
public class PurchaseQuestionNotifier {

    private static final Logger log = LoggerFactory.getLogger(PurchaseQuestionNotifier.class);

    private final WhatsappLinkRepository linkRepository;
    private final PurchaseNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final WhatsappClient whatsapp;
    private final TransactionTemplate tx;
    private final TaskExecutor executor;
    private final String templateName;
    private final String templateLanguage;
    private final int maxPerDay;

    public PurchaseQuestionNotifier(
            WhatsappLinkRepository linkRepository,
            PurchaseNoteRepository noteRepository,
            UserRepository userRepository,
            WhatsappClient whatsapp,
            TransactionTemplate tx,
            @Qualifier("whatsappExecutor") TaskExecutor executor,
            @Value("${nexorix.whatsapp.template-name:}") String templateName,
            @Value("${nexorix.whatsapp.template-language:es}") String templateLanguage,
            @Value("${nexorix.whatsapp.max-questions-per-day:20}") int maxPerDay
    ) {
        this.linkRepository = linkRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.whatsapp = whatsapp;
        this.tx = tx;
        this.executor = executor;
        this.templateName = templateName == null ? "" : templateName.trim();
        this.templateLanguage = templateLanguage;
        this.maxPerDay = maxPerDay;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchase(PurchaseRegisteredEvent event) {
        if (whatsapp.isEnabled()) {
            executor.execute(() -> ask(event));
        }
    }

    private record Pending(Long noteId, String phone) {
    }

    void ask(PurchaseRegisteredEvent event) {
        try {
            Pending pending = tx.execute(status -> createNote(event));
            if (pending == null) {
                return;
            }

            String amount = money(event.amount());
            String messageId;
            try {
                messageId = templateName.isEmpty()
                        // Sin plantilla solo funciona si la persona escribio en las ultimas 24 horas (pruebas).
                        ? whatsapp.sendText(pending.phone(), "🛒 Nexorix: registraste un gasto de " + amount
                        + " (" + event.description() + ").\n¿Qué compraste? Respóndeme con una nota de voz 🎙️")
                        : whatsapp.sendTemplate(pending.phone(), templateName, templateLanguage,
                        List.of(amount, event.description()));
            } catch (WhatsappException exception) {
                log.warn("No se pudo preguntar por WhatsApp la compra {}: {}", event.transactionId(), exception.getMessage());
                tx.executeWithoutResult(status -> noteRepository.findById(pending.noteId())
                        .ifPresent(note -> note.failed(exception.getMessage())));
                return;
            }

            tx.executeWithoutResult(status -> noteRepository.findById(pending.noteId())
                    .ifPresent(note -> note.questionSent(messageId)));

        } catch (Exception exception) {
            log.error("Error preguntando por WhatsApp la compra {}", event.transactionId(), exception);
        }
    }

    /** Crea la fila de la compra si la persona tiene WhatsApp verificado y no paso el limite diario. */
    private Pending createNote(PurchaseRegisteredEvent event) {
        WhatsappLink link = linkRepository.findByUserId(event.userId()).orElse(null);
        if (link == null || !link.canReceiveQuestions()) {
            return null;
        }
        long today = noteRepository.countByUserIdAndCreatedAtAfter(event.userId(), LocalDate.now().atStartOfDay());
        if (today >= maxPerDay) {
            log.info("Limite diario de preguntas por WhatsApp alcanzado para el usuario {}", event.userId());
            return null;
        }
        User user = userRepository.getReferenceById(event.userId());
        PurchaseNote note = noteRepository.save(new PurchaseNote(user, event.transactionId(), event.amount(),
                event.description(), event.transactionDate()));
        return new Pending(note.getId(), link.getPhone());
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        format.setMaximumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return format.format(amount);
    }
}
