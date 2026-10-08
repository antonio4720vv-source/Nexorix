package com.nexorix.whatsapp;

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
 * Verifica que un webhook realmente venga de Meta (WhatsApp).
 *
 * Meta firma el cuerpo EXACTO con HMAC-SHA256 usando el "secreto de la app"
 * y lo manda en X-Hub-Signature-256 como "sha256=<hex>".
 * Igual que con Didit: sin secreto configurado se rechaza todo (falla cerrado).
 */
@Component
public class WhatsappSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final String appSecret;
    private final String verifyToken;

    public WhatsappSignatureVerifier(
            @Value("${nexorix.whatsapp.app-secret:}") String appSecret,
            @Value("${nexorix.whatsapp.verify-token:}") String verifyToken
    ) {
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.verifyToken = verifyToken == null ? "" : verifyToken.trim();
    }

    /** Lanza WebhookVerificationException si la firma no es valida; IllegalStateException si falta el secreto. */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (appSecret.isEmpty()) {
            throw new IllegalStateException("Falta la variable de entorno WHATSAPP_APP_SECRET.");
        }
        if (signatureHeader == null || !signatureHeader.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new WebhookVerificationException("Falta la cabecera X-Hub-Signature-256.");
        }
        String received = signatureHeader.trim().substring(PREFIX.length()).toLowerCase(Locale.ROOT);
        String expected = hmacHex(rawBody == null ? new byte[0] : rawBody);

        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.getBytes(StandardCharsets.US_ASCII))) {
            throw new WebhookVerificationException("Firma de WhatsApp invalida.");
        }
    }

    /**
     * Cuando se configura el webhook en Meta, Meta llama con un GET y un
     * hub.verify_token que debe ser igual al nuestro.
     */
    public boolean subscriptionTokenMatches(String token) {
        return !verifyToken.isEmpty() && token != null
                && MessageDigest.isEqual(verifyToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular la firma HMAC.", exception);
        }
    }
}
