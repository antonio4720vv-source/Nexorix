package com.nexorix.banking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneMatcherTest {

    @Test
    void ignoraFormatoEIndicativo() {
        assertTrue(PhoneMatcher.same("+57 300 123 4567", "3001234567"));
        assertTrue(PhoneMatcher.same("573001234567", "300-123-4567"));
    }

    @Test
    void numerosDistintosOVaciosNoCoinciden() {
        assertFalse(PhoneMatcher.same("3001234567", "3001234568"));
        assertFalse(PhoneMatcher.same(null, "3001234567"));
        assertFalse(PhoneMatcher.same("123", "123"));
    }
}
