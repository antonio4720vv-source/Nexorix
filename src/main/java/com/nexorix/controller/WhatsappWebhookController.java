package com.nexorix.controller;

import com.nexorix.user.WebhookVerificationException;
import com.nexorix.whatsapp.WhatsappInboundService;
import com.nexorix.whatsapp.WhatsappSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook de WhatsApp Cloud (Meta). Configurarlo en
 * Meta for Developers > WhatsApp > Configuracion > Webhook:
 *   URL: {NEXORIX_PUBLIC_URL}/api/whatsapp/webhook
 *   Token de verificacion: WHATSAPP_VERIFY_TOKEN
 *   Suscribirse al campo "messages".
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final WhatsappSignatureVerifier verifier;
    private final WhatsappInboundService inboundService;
    private final ObjectMapper objectMapper;

    public WhatsappWebhookController(
            WhatsappSignatureVerifier verifier,
            WhatsappInboundService inboundService,
            ObjectMapper objectMapper
    ) {
        this.verifier = verifier;
        this.inboundService = inboundService;
        this.objectMapper = objectMapper;
    }

    /** Meta confirma que el webhook es nuestro: hay que devolver hub.challenge. */
    @GetMapping
    public ResponseEntity<String> subscribe(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equals(mode) && challenge != null && verifier.subscriptionTokenMatches(token)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge.replaceAll("[^0-9A-Za-z_-]", ""));
        }
        return ResponseEntity.status(403).body("Token invalido");
    }

    /** Cuerpo como bytes crudos: la firma se calcula sobre los bytes exactos. */
    @PostMapping
    public ResponseEntity<String> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) {
        try {
            verifier.verify(rawBody, signature);
        } catch (WebhookVerificationException exception) {
            log.warn("Webhook de WhatsApp RECHAZADO: {}", exception.getMessage());
            return ResponseEntity.status(401).body("Firma invalida");
        } catch (IllegalStateException exception) {
            log.error("Webhook de WhatsApp no procesado: {}", exception.getMessage());
            return ResponseEntity.status(503).body("Webhook no configurado");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body("JSON invalido");
        }

        try {
            // Responder rapido: Meta reintenta si tardamos. Las notas de voz se procesan en otro hilo.
            int accepted = inboundService.receive(payload);
            return ResponseEntity.ok("OK " + accepted);
        } catch (Exception exception) {
            log.error("Error recibiendo el webhook de WhatsApp", exception);
            return ResponseEntity.internalServerError().body("Error interno");
        }
    }
}
