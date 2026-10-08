package com.nexorix.controller;

import com.nexorix.user.DiditWebhookService;
import com.nexorix.user.DiditWebhookVerifier;
import com.nexorix.user.WebhookVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/kyc")
public class KycWebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(KycWebhookController.class);

    private final DiditWebhookVerifier webhookVerifier;
    private final DiditWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public KycWebhookController(
            DiditWebhookVerifier webhookVerifier,
            DiditWebhookService webhookService,
            ObjectMapper objectMapper
    ) {
        this.webhookVerifier = webhookVerifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // WEBHOOK DIDIT
    // ============================================================

    /**
     * Recibe el cuerpo como bytes crudos (byte[]) y NO como Map:
     * la firma de Didit se calcula sobre los bytes exactos, y si Spring
     * convirtiera el JSON a objeto y lo volviera a armar, la firma
     * dejaria de coincidir.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp
    ) {

        // 1. Seguridad: verificar firma y timestamp ANTES de leer el contenido.
        try {

            webhookVerifier.verify(rawBody, signature, timestamp);

        } catch (WebhookVerificationException exception) {

            log.warn("Webhook RECHAZADO: {}", exception.getMessage());

            // 401: Didit no reintenta los 4xx (salvo 404).
            return ResponseEntity.status(401).body("Firma invalida");

        } catch (IllegalStateException exception) {

            log.error("Webhook no procesado por configuracion: {}",
                    exception.getMessage());

            // 503: problema nuestro; Didit reintentara mas tarde.
            return ResponseEntity.status(503).body("Webhook no configurado");
        }

        // 2. Leer el JSON (ya sabemos que viene de Didit).
        JsonNode payload;

        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            log.warn("Webhook con JSON invalido: {}", exception.getMessage());
            return ResponseEntity.badRequest().body("JSON invalido");
        }

        // 3. Procesar.
        try {

            DiditWebhookService.Result result = webhookService.handle(payload);

            log.info("Webhook Didit resultado: {}", result);

            return ResponseEntity.ok(result.name());

        } catch (DataIntegrityViolationException exception) {

            // Dos entregas del mismo evento llegaron al mismo tiempo:
            // la otra ya lo registro.
            log.info("Webhook repetido (entrega simultanea).");
            return ResponseEntity.ok("DUPLICATE");

        } catch (IllegalArgumentException exception) {

            log.warn("Webhook con datos incompletos: {}", exception.getMessage());
            return ResponseEntity.badRequest().body(exception.getMessage());

        } catch (Exception exception) {

            log.error("Error procesando webhook de Didit", exception);

            // 500: Didit reintentara (~1 min y ~4 min despues).
            return ResponseEntity.internalServerError().body("Error interno");
        }
    }
}
