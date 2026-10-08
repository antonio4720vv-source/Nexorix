package com.nexorix.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Cifrado de datos sensibles guardados en la base de datos (cedula, correo, celular).
 *
 * - AES-256-GCM con un nonce aleatorio por valor: cifra y ademas detecta si alguien
 *   modifico el dato guardado.
 * - "Indice ciego" (HMAC-SHA256 con otra clave derivada): permite buscar por correo o
 *   cedula y exigir que sean unicos sin guardarlos en claro.
 * - Los valores viejos sin cifrar se siguen leyendo (migracion suave) y se cifran al
 *   arrancar (DataProtectionMigrator).
 *
 * La clave llega por NEXORIX_DATA_KEY (openssl rand -base64 32). Si se pierde, los datos
 * cifrados no se pueden recuperar: guardala en un lugar seguro, aparte de la base de datos.
 */
public final class FieldCipher {

    static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec encryptionKey;
    private static volatile SecretKeySpec indexKey;

    private FieldCipher() {
    }

    /** Configura las claves a partir del secreto maestro. null o vacio = sin cifrado (solo desarrollo). */
    public static synchronized void configure(String masterSecret) {
        if (masterSecret == null || masterSecret.isBlank()) {
            encryptionKey = null;
            indexKey = null;
            return;
        }
        byte[] master = decodeMaster(masterSecret.trim());
        encryptionKey = new SecretKeySpec(derive(master, "nexorix/field-encryption/v1"), "AES");
        indexKey = new SecretKeySpec(derive(master, "nexorix/blind-index/v1"), "HmacSHA256");
    }

    public static boolean isEnabled() {
        return encryptionKey != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plain) {
        if (plain == null || encryptionKey == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(nonce.length + encrypted.length);
            packed.put(nonce).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(packed.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el dato.", exception);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored; // dato viejo sin cifrar
        }
        if (encryptionKey == null) {
            throw new IllegalStateException(
                    "Hay datos cifrados pero falta NEXORIX_DATA_KEY. Configura la misma clave con la que se cifraron.");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(TAG_BITS, packed, 0, NONCE_BYTES));
            byte[] plain = cipher.doFinal(packed, NONCE_BYTES, packed.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("No se pudo descifrar el dato (clave incorrecta o dato alterado).", exception);
        }
    }

    /**
     * Huella para buscar sin guardar el valor en claro. Sin clave configurada devuelve el valor
     * normalizado, asi el modo desarrollo se comporta igual que antes.
     */
    public static String blindIndex(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (indexKey == null) {
            return normalized;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            return java.util.HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo calcular el indice del dato.", exception);
        }
    }

    /** Comparacion en tiempo constante (para secretos). */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] decodeMaster(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // no es Base64: se trata como texto
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "NEXORIX_DATA_KEY es muy corta. Genera una con: openssl rand -base64 32");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /** HKDF simplificado: una clave distinta por uso a partir del secreto maestro. */
    private static byte[] derive(byte[] master, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
