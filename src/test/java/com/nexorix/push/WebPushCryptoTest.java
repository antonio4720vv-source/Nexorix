package com.nexorix.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    /** Lo que haria el navegador (RFC 8291 §4): con su llave privada y el secreto de autenticacion descifra el aviso. */
    private static String decryptAsBrowser(byte[] body, KeyPair browser, byte[] authSecret) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idLength = body[20] & 0xff;
        byte[] serverPublic = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] cipherText = Arrays.copyOfRange(body, 21 + idLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.decodePublic(serverPublic), true);
        byte[] shared = agreement.generateSecret();

        byte[] browserPublic = WebPushCrypto.encodePublic(browser.getPublic());
        byte[] info = new byte[14 + 65 + 65];
        System.arraycopy("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), 0, info, 0, 14);
        System.arraycopy(browserPublic, 0, info, 14, 65);
        System.arraycopy(serverPublic, 0, info, 79, 65);

        byte[] ikm = WebPushCrypto.hkdf(authSecret, shared, info, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(cipherText);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2); // delimitador del ultimo registro
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    @Test
    void elNavegadorPuedeDescifrarElAviso() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);

        byte[] body = WebPushCrypto.encrypt("{\"title\":\"Compra bloqueada 🚨\"}".getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.b64(WebPushCrypto.encodePublic(browser.getPublic())), WebPushCrypto.b64(authSecret));

        assertThat(decryptAsBrowser(body, browser, authSecret)).isEqualTo("{\"title\":\"Compra bloqueada 🚨\"}");
        // Encabezado: salt(16) + tamano de registro 4096 + largo de llave 65.
        assertThat(Arrays.copyOfRange(body, 16, 20)).containsExactly(0, 0, 16, 0);
        assertThat(body[20]).isEqualTo((byte) 65);
    }

    @Test
    void otroNavegadorNoPuedeDescifrarlo() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] authSecret = new byte[16];
        byte[] body = WebPushCrypto.encrypt("secreto".getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.b64(WebPushCrypto.encodePublic(browser.getPublic())), WebPushCrypto.b64(authSecret));

        assertThatThrownBy(() -> decryptAsBrowser(body, WebPushCrypto.newKeyPair(), authSecret))
                .isInstanceOf(java.security.GeneralSecurityException.class);
    }

    @Test
    void unAvisoDemasiadoLargoSeRechaza() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[5000],
                WebPushCrypto.b64(WebPushCrypto.encodePublic(browser.getPublic())), WebPushCrypto.b64(new byte[16])))
                .isInstanceOf(java.security.GeneralSecurityException.class);
    }

    @Test
    void laFirmaVapidSeVerificaConLaLlavePublica() throws Exception {
        KeyPair vapid = WebPushCrypto.newKeyPair();
        String jwt = WebPushCrypto.vapidJwt("https://fcm.googleapis.com", "mailto:a@b.co", 2_000_000_000L, vapid.getPrivate());

        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(new String(WebPushCrypto.unb64(parts[0]), StandardCharsets.UTF_8)).contains("ES256");
        assertThat(new String(WebPushCrypto.unb64(parts[1]), StandardCharsets.UTF_8))
                .contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":2000000000");

        byte[] raw = WebPushCrypto.unb64(parts[2]);
        assertThat(raw).hasSize(64);
        // Se reconstruye la firma DER a partir de R || S y se verifica como lo haria el servicio de push.
        Signature verify = Signature.getInstance("SHA256withECDSA");
        verify.initVerify(vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(rawToDer(raw))).isTrue();
    }

    private static byte[] rawToDer(byte[] raw) {
        byte[] r = new java.math.BigInteger(1, Arrays.copyOfRange(raw, 0, 32)).toByteArray();
        byte[] s = new java.math.BigInteger(1, Arrays.copyOfRange(raw, 32, 64)).toByteArray();
        byte[] der = new byte[6 + r.length + s.length];
        der[0] = 0x30;
        der[1] = (byte) (4 + r.length + s.length);
        der[2] = 0x02;
        der[3] = (byte) r.length;
        System.arraycopy(r, 0, der, 4, r.length);
        der[4 + r.length] = 0x02;
        der[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, der, 6 + r.length, s.length);
        return der;
    }

    @Test
    void soloSeLeEscribeAServiciosDePushConocidos() {
        assertThat(WebPushClient.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(WebPushClient.isAllowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
        assertThat(WebPushClient.isAllowedEndpoint("https://web.push.apple.com/abc")).isTrue();
        assertThat(WebPushClient.isAllowedEndpoint("https://wns2-par02p.notify.windows.com/w/?token=1")).isTrue();
        assertThat(WebPushClient.isAllowedEndpoint("http://fcm.googleapis.com/x")).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint("https://evil.com/fcm.googleapis.com")).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint("https://fcm.googleapis.com.evil.com/x")).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint("https://localhost/x")).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint("https://user@fcm.googleapis.com/x")).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint("no es url")).isFalse();
    }
}
