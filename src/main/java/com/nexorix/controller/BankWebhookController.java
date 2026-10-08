package com.nexorix.controller;

import com.nexorix.banking.BankSignatureVerifier;
import com.nexorix.banking.BankSyncService;
import com.nexorix.banking.BankWebhookPayload;
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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Webhook del agregador bancario (Plaid / Prometeo / mock). Publico pero firmado:
 * X-Bank-Signature = sha256=HMAC(BANK_WEBHOOK_SECRET, cuerpo exacto).
 */
@RestController
@RequestMapping("/api/bank/webhook")
public class BankWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BankWebhookController.class);

    private final BankSignatureVerifier verifier;
    private final BankSyncService sync;
    private final ObjectMapper objectMapper;

    public BankWebhookController(BankSignatureVerifier verifier, BankSyncService sync, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.sync = sync;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Bank-Signature", required = false) String signature
    ) {
        try {
            verifier.verify(rawBody, signature);
        } catch (WebhookVerificationException exception) {
            log.warn("Webhook bancario RECHAZADO: {}", exception.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Firma invalida"));
        } catch (IllegalStateException exception) {
            log.error("Webhook bancario no procesado: {}", exception.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Webhook no configurado"));
        }
        try {
            BankWebhookPayload payload = objectMapper.readValue(rawBody, BankWebhookPayload.class);
            return ResponseEntity.ok(sync.process(payload));
        } catch (DataIntegrityViolationException duplicate) {
            // Dos entregas del mismo evento al mismo tiempo: la segunda se descarta.
            return ResponseEntity.ok(Map.of("status", "DUPLICATE"));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", exception.getReason()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            log.error("Error procesando el webhook bancario", exception);
            return ResponseEntity.badRequest().body(Map.of("error", "Payload invalido"));
        }
    }
}
