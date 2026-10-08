package com.nexorix.banking;

import com.nexorix.user.WebhookVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifica que el webhook venga del agregador: HMAC-SHA256 del cuerpo exacto con BANK_WEBHOOK_SECRET,
 * en la cabecera X-Bank-Signature como "sha256=<hex>". Sin secreto se rechaza todo (falla cerrado).
 */
@Component
public class BankSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final String secret;

    public BankSignatureVerifier(@Value("${nexorix.bank.webhook-secret:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public void verify(byte[] rawBody, String header) {
        if (secret.isEmpty()) {
            throw new IllegalStateException("Falta la variable de entorno BANK_WEBHOOK_SECRET.");
        }
        if (header == null || !header.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new WebhookVerificationException("Falta la cabecera X-Bank-Signature.");
        }
        String received = header.trim().substring(PREFIX.length()).toLowerCase(Locale.ROOT);
        if (!MessageDigest.isEqual(sign(rawBody == null ? new byte[0] : rawBody).getBytes(StandardCharsets.US_ASCII),
                received.getBytes(StandardCharsets.US_ASCII))) {
            throw new WebhookVerificationException("Firma del banco invalida.");
        }
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular la firma HMAC.", exception);
        }
    }
}
