package com.nexorix.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;

/**
 * Las dos piezas criptograficas de Web Push, solo con el JDK:
 *  - RFC 8291 (aes128gcm): cifra el mensaje para que solo el navegador de la persona lo lea.
 *  - RFC 8292 (VAPID): firma con ES256 para que el servicio de push sepa que somos nosotros.
 */
final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {
    }

    static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    static byte[] unb64(String text) {
        return Base64.getUrlDecoder().decode(text.replace('+', '-').replace('/', '_').replaceAll("=+$", ""));
    }

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /** Par de llaves P-256 nuevo. */
    static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** Punto publico sin comprimir: 0x04 || X || Y (65 bytes). */
    static byte[] encodePublic(PublicKey key) {
        ECPoint point = ((ECPublicKey) key).getW();
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed(point.getAffineX(), 32), 0, out, 1, 32);
        System.arraycopy(fixed(point.getAffineY(), 32), 0, out, 33, 32);
        return out;
    }

    static PublicKey decodePublic(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("Llave publica P-256 invalida.");
        }
        BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(raw, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve()));
    }

    static PrivateKey decodePrivate(byte[] raw) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), curve()));
    }

    static byte[] encodePrivate(PrivateKey key) {
        return fixed(((java.security.interfaces.ECPrivateKey) key).getS(), 32);
    }

    private static byte[] fixed(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[size];
        int copy = Math.min(bytes.length, size);
        System.arraycopy(bytes, bytes.length - copy, out, size - copy, copy);
        return out;
    }

    // ---------------- RFC 8291 ----------------

    /** Cuerpo cifrado listo para enviar al servicio de push. */
    static byte[] encrypt(byte[] plaintext, String p256dh, String authSecret) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, unb64(p256dh), unb64(authSecret), newKeyPair(), salt);
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair server, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > RECORD_SIZE - 17 - 1) {
            throw new GeneralSecurityException("El aviso es demasiado largo.");
        }
        byte[] serverPublic = encodePublic(server.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(server.getPrivate());
        agreement.doPhase(decodePublic(uaPublic), true);
        byte[] shared = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, serverPublic);
        byte[] ikm = hkdf(authSecret, shared, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = concat(plaintext, new byte[]{2}); // 0x02 = ultimo (y unico) registro
        byte[] encrypted = cipher.doFinal(padded);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(salt);
        body.writeBytes(ByteBuffer.allocate(4).putInt(RECORD_SIZE).array());
        body.write(serverPublic.length);
        body.writeBytes(serverPublic);
        body.writeBytes(encrypted);
        return body.toByteArray();
    }

    /** HKDF-SHA256 (extract + expand) para salidas de hasta 32 bytes. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] okm = hmac(prk, concat(info, new byte[]{1}));
        return java.util.Arrays.copyOf(okm, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.length == 0 ? new byte[32] : key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // ---------------- RFC 8292 ----------------

    /** JWT ES256 para el encabezado Authorization: vapid. */
    static String vapidJwt(String audience, String subject, long expiresAtEpochSeconds, PrivateKey key)
            throws GeneralSecurityException {
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtEpochSeconds
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64(derToRaw(signature.sign()));
    }

    /** La firma del JDK viene en DER; JWT la quiere como R || S de 64 bytes. */
    static byte[] derToRaw(byte[] der) {
        int offset = 2;
        if ((der[1] & 0x80) != 0) {
            offset += der[1] & 0x7f;
        }
        int rLength = der[offset + 1];
        byte[] r = java.util.Arrays.copyOfRange(der, offset + 2, offset + 2 + rLength);
        int sOffset = offset + 2 + rLength;
        int sLength = der[sOffset + 1];
        byte[] s = java.util.Arrays.copyOfRange(der, sOffset + 2, sOffset + 2 + sLength);
        byte[] out = new byte[64];
        System.arraycopy(fixed(new BigInteger(1, r), 32), 0, out, 0, 32);
        System.arraycopy(fixed(new BigInteger(1, s), 32), 0, out, 32, 32);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
