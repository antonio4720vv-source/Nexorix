package com.nexorix.whatsapp;

import com.nexorix.user.WebhookVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsappSignatureVerifierTest {

    private final WhatsappSignatureVerifier verifier = new WhatsappSignatureVerifier("secreto", "token-meta");
    private final byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

    @Test
    void aceptaLaFirmaCorrecta() {
        String signature = "sha256=" + verifier.hmacHex(body);
        assertThatCode(() -> verifier.verify(body, signature)).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnCuerpoModificado() {
        String signature = "sha256=" + verifier.hmacHex(body);
        byte[] changed = "{\"entry\":[1]}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(changed, signature)).isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void rechazaSinFirma() {
        assertThatThrownBy(() -> verifier.verify(body, null)).isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void sinSecretoFallaCerrado() {
        WhatsappSignatureVerifier empty = new WhatsappSignatureVerifier("", "");
        assertThatThrownBy(() -> empty.verify(body, "sha256=abc")).isInstanceOf(IllegalStateException.class);
        assertThat(empty.subscriptionTokenMatches("")).isFalse();
    }

    @Test
    void elTokenDeSuscripcionDebeCoincidir() {
        assertThat(verifier.subscriptionTokenMatches("token-meta")).isTrue();
        assertThat(verifier.subscriptionTokenMatches("otro")).isFalse();
        assertThat(verifier.subscriptionTokenMatches(null)).isFalse();
    }
}
