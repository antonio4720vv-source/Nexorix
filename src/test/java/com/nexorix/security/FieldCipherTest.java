package com.nexorix.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldCipherTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef-test";

    @AfterEach
    void reset() {
        FieldCipher.configure(null);
    }

    @Test
    void encryptsAndDecryptsWithRandomNonce() {
        FieldCipher.configure(KEY);
        String a = FieldCipher.encrypt("1020304050");
        String b = FieldCipher.encrypt("1020304050");
        assertTrue(a.startsWith("enc:v1:"));
        assertNotEquals(a, b);
        assertFalse(a.contains("1020304050"));
        assertEquals("1020304050", FieldCipher.decrypt(a));
    }

    @Test
    void readsLegacyPlaintextAndDoesNotDoubleEncrypt() {
        FieldCipher.configure(KEY);
        assertEquals("ana@mail.com", FieldCipher.decrypt("ana@mail.com"));
        String once = FieldCipher.encrypt("x");
        assertEquals(once, FieldCipher.encrypt(once));
    }

    @Test
    void detectsTampering() {
        FieldCipher.configure(KEY);
        String stored = FieldCipher.encrypt("secreto");
        char last = stored.charAt(stored.length() - 3);
        String tampered = stored.substring(0, stored.length() - 3) + (last == 'A' ? 'B' : 'A') + stored.substring(stored.length() - 2);
        assertThrows(IllegalStateException.class, () -> FieldCipher.decrypt(tampered));
    }

    @Test
    void wrongKeyFails() {
        FieldCipher.configure(KEY);
        String stored = FieldCipher.encrypt("secreto");
        FieldCipher.configure(KEY + "-otra");
        assertThrows(IllegalStateException.class, () -> FieldCipher.decrypt(stored));
    }

    @Test
    void blindIndexIsStableCaseInsensitiveAndHidesValue() {
        FieldCipher.configure(KEY);
        String i1 = FieldCipher.blindIndex("Ana@Mail.com ");
        assertEquals(i1, FieldCipher.blindIndex("ana@mail.com"));
        assertNotEquals(i1, FieldCipher.blindIndex("otra@mail.com"));
        assertEquals(64, i1.length());
    }

    @Test
    void shortKeyIsRejected() {
        assertThrows(IllegalStateException.class, () -> FieldCipher.configure("corta"));
    }

    @Test
    void disabledModeIsPassThrough() {
        assertFalse(FieldCipher.isEnabled());
        assertEquals("abc", FieldCipher.encrypt("abc"));
        assertEquals("abc", FieldCipher.blindIndex("ABC"));
    }
}
