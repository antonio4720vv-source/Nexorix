package com.nexorix.user;

import com.nexorix.auth.RecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Procesa un webhook de Didit que YA paso la verificacion de firma.
 * Se encarga de ignorar repetidos (event_id) y de delegar en KycService.
 */
@Service
public class DiditWebhookService {

    private static final Logger log =
            LoggerFactory.getLogger(DiditWebhookService.class);

    public enum Result {
        /** Se actualizo una verificacion de Nexorix. */
        PROCESSED,
        /** Valido, pero no cambio nada (atrasado, sesion desconocida...). */
        NO_CHANGES,
        /** Este event_id ya se habia procesado. */
        DUPLICATE,
        /** Webhook de prueba enviado desde el panel de Didit. */
        TEST,
        /** Tipo de evento que Nexorix no usa todavia. */
        IGNORED_TYPE
    }

    private final KycService kycService;
    private final RecoveryService recoveryService;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;

    public DiditWebhookService(
            KycService kycService,
            RecoveryService recoveryService,
            ProcessedWebhookEventRepository processedWebhookEventRepository
    ) {
        this.kycService = kycService;
        this.recoveryService = recoveryService;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
    }

    @Transactional
    public Result handle(JsonNode payload) {

        String eventId = text(payload, "event_id");
        String webhookType = text(payload, "webhook_type");
        String sessionId = text(payload, "session_id");
        String status = text(payload, "status");
        String environment = text(payload, "environment");

        log.info("Webhook Didit | evento: {} | tipo: {} | sesion: {} | "
                        + "estado: {} | entorno: {}",
                eventId, webhookType, sessionId, status, environment);

        if (isTestWebhook(payload)) {
            log.info("Webhook de prueba de Didit. No se modifica ningun usuario.");
            return Result.TEST;
        }

        if (eventId == null) {
            throw new IllegalArgumentException(
                    "El webhook de Didit no contiene event_id."
            );
        }

        if (processedWebhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook repetido ignorado. event_id: {}", eventId);
            return Result.DUPLICATE;
        }

        Result result;

        if ("status.updated".equals(webhookType)) {

            // Primero: ¿es la verificacion de una recuperacion de cuenta?
            Optional<Boolean> recovery = recoveryService.processWebhook(
                    sessionId, status, payload.get("decision"));

            boolean changed = recovery.isPresent()
                    ? recovery.get()
                    // Si no, es la verificacion de identidad del registro.
                    : kycService.processDiditWebhook(sessionId, status, false);

            result = changed ? Result.PROCESSED : Result.NO_CHANGES;

        } else {

            log.info("Tipo de webhook no usado por Nexorix: {}", webhookType);
            result = Result.IGNORED_TYPE;
        }

        processedWebhookEventRepository.save(
                new ProcessedWebhookEvent(
                        eventId, "DIDIT", webhookType, sessionId, status
                )
        );

        return result;
    }

    private boolean isTestWebhook(JsonNode payload) {

        JsonNode flag = payload.path("metadata").path("test_webhook");

        if (flag.isBoolean()) {
            return flag.asBoolean();
        }

        return flag.isValueNode()
                && Boolean.parseBoolean(flag.asText());
    }

    private String text(JsonNode payload, String field) {

        JsonNode value = payload.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        return text.isBlank() ? null : text;
    }
}
