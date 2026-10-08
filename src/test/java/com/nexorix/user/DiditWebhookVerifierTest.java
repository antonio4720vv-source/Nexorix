package com.nexorix.user;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiditWebhookVerifierTest {

    private static final String SECRET = "secreto-de-prueba";
    private static final long NOW = 1_800_000_000L;
    private static final byte[] BODY =
            "{\"event_id\":\"e1\",\"status\":\"Approved\"}"
                    .getBytes(StandardCharsets.UTF_8);

    private final Clock fixedClock =
            Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);

    private final DiditWebhookVerifier verifier =
            new DiditWebhookVerifier(SECRET, fixedClock);

    /** Firma calculada de forma independiente, como lo haria Didit. */
    private static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void aceptaUnWebhookConFirmaCorrecta() throws Exception {
        String signature = sign(SECRET, BODY);

        assertThatCode(() ->
                verifier.verify(BODY, signature, String.valueOf(NOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void aceptaLaFirmaEnMayusculas() throws Exception {
        String signature = sign(SECRET, BODY).toUpperCase();

        assertThatCode(() ->
                verifier.verify(BODY, signature, String.valueOf(NOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaUnaFirmaHechaConOtroSecreto() throws Exception {
        String signature = sign("secreto-falso", BODY);

        assertThatThrownBy(() ->
                verifier.verify(BODY, signature, String.valueOf(NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("Firma invalida");
    }

    @Test
    void rechazaSiAlguienModificoElCuerpo() throws Exception {
        String signature = sign(SECRET, BODY);
        byte[] modified =
                "{\"event_id\":\"e1\",\"status\":\"Declined\"}"
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                verifier.verify(modified, signature, String.valueOf(NOW)))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void rechazaUnWebhookViejo() throws Exception {
        String signature = sign(SECRET, BODY);
        long viejo = NOW - DiditWebhookVerifier.MAX_AGE_SECONDS - 1;

        assertThatThrownBy(() ->
                verifier.verify(BODY, signature, String.valueOf(viejo)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("ventana de tiempo");
    }

    @Test
    void rechazaUnTimestampDelFuturo() throws Exception {
        String signature = sign(SECRET, BODY);
        long futuro = NOW + DiditWebhookVerifier.MAX_AGE_SECONDS + 1;

        assertThatThrownBy(() ->
                verifier.verify(BODY, signature, String.valueOf(futuro)))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void aceptaUnWebhookDentroDeLaVentana() throws Exception {
        String signature = sign(SECRET, BODY);
        long hace4Minutos = NOW - 240;

        assertThatCode(() ->
                verifier.verify(BODY, signature, String.valueOf(hace4Minutos)))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaSiFaltaLaFirma() {
        assertThatThrownBy(() ->
                verifier.verify(BODY, null, String.valueOf(NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("X-Signature");
    }

    @Test
    void rechazaSiFaltaElTimestamp() throws Exception {
        String signature = sign(SECRET, BODY);

        assertThatThrownBy(() ->
                verifier.verify(BODY, signature, null))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("X-Timestamp");
    }

    @Test
    void rechazaUnTimestampQueNoEsNumero() throws Exception {
        String signature = sign(SECRET, BODY);

        assertThatThrownBy(() ->
                verifier.verify(BODY, signature, "ayer"))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void sinSecretoConfiguradoRechazaTodo() throws Exception {
        DiditWebhookVerifier sinSecreto =
                new DiditWebhookVerifier("", fixedClock);
        String signature = sign(SECRET, BODY);

        assertThatThrownBy(() ->
                sinSecreto.verify(BODY, signature, String.valueOf(NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIDIT_WEBHOOK_SECRET");
    }
}
