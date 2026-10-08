package com.nexorix.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifica que un webhook realmente venga de Didit.
 *
 * Didit firma el cuerpo EXACTO de la peticion (los bytes tal cual llegan)
 * con HMAC-SHA256 usando el "secreto de firma" del webhook, y envia:
 *   - X-Signature: la firma en hexadecimal
 *   - X-Timestamp: el momento del envio (segundos Unix)
 *
 * Nexorix recalcula la firma con el mismo secreto y la compara.
 * Si alguien modifica el cuerpo o no conoce el secreto, la firma no coincide.
 * El timestamp evita que un webhook viejo capturado se reenvie mas tarde.
 */
@Component
public class DiditWebhookVerifier {

    private static final Logger log =
            LoggerFactory.getLogger(DiditWebhookVerifier.class);

    /** Maxima diferencia permitida entre X-Timestamp y la hora actual. */
    static final long MAX_AGE_SECONDS = 300;

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;
    private final Clock clock;

    @Autowired
    public DiditWebhookVerifier(
            @Value("${didit.webhook.secret:}") String secret
    ) {
        this(secret, Clock.systemUTC());
    }

    /** Constructor para pruebas: permite fijar la hora. */
    DiditWebhookVerifier(String secret, Clock clock) {
        this.secret = secret == null ? "" : secret.trim();
        this.clock = clock;

        log.info("Didit webhook secret configurado: {} ({} caracteres)",
                !this.secret.isEmpty(), this.secret.length());
    }

    /**
     * Lanza WebhookVerificationException si el webhook no es valido.
     * Lanza IllegalStateException si falta configurar el secreto.
     */
    public void verify(
            byte[] rawBody,
            String signatureHeader,
            String timestampHeader
    ) {

        if (secret.isEmpty()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno DIDIT_WEBHOOK_SECRET."
            );
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookVerificationException(
                    "Falta la cabecera X-Signature."
            );
        }

        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new WebhookVerificationException(
                    "Falta la cabecera X-Timestamp."
            );
        }

        long timestamp;

        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException exception) {
            throw new WebhookVerificationException(
                    "X-Timestamp no es un numero valido."
            );
        }

        long now = clock.instant().getEpochSecond();

        if (Math.abs(now - timestamp) > MAX_AGE_SECONDS) {
            throw new WebhookVerificationException(
                    "Webhook fuera de la ventana de tiempo permitida ("
                            + (now - timestamp) + " segundos de diferencia)."
            );
        }

        byte[] body = rawBody == null ? new byte[0] : rawBody;

        String expected = hmacHex(body);
        String received = signatureHeader.trim().toLowerCase(Locale.ROOT);

        // Comparacion en tiempo constante: no revela cuantos caracteres
        // coinciden, para que nadie pueda adivinar la firma por tiempos.
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                received.getBytes(StandardCharsets.US_ASCII)
        );

        if (!matches) {
            throw new WebhookVerificationException(
                    "Firma invalida (cuerpo de " + body.length + " bytes)."
            );
        }
    }

    String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            ));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible calcular la firma HMAC.", exception
            );
        }
    }
}
