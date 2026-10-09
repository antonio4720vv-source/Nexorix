package com.nexorix.account;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardInfoTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test
    void guardaSoloMarcaUltimosCuatroYVencimiento() {
        CardInfo visa = CardInfo.of("4111 1111 1111 1111", "12/30", TODAY);
        assertEquals("VISA", visa.brand());
        assertEquals("1111", visa.last4());
        assertEquals(2030, visa.expYear());
        assertEquals("MASTERCARD", CardInfo.of("5555-5555-5555-4444", "09/2029", TODAY).brand());
    }

    @Test
    void rechazaNumerosInvalidosYTarjetasVencidas() {
        assertThrows(IllegalArgumentException.class, () -> CardInfo.of("4111 1111 1111 1112", "12/30", TODAY));
        assertThrows(IllegalArgumentException.class, () -> CardInfo.of("4111111111111111", "09/26", TODAY));
        assertThrows(IllegalArgumentException.class, () -> CardInfo.of("4111111111111111", "13/30", TODAY));
        assertThrows(IllegalArgumentException.class, () -> CardInfo.of("4111111111111111", "1230", TODAY));
    }

    @Test
    void laTarjetaVenceElUltimoDiaDelMes() {
        Account a = new Account("T", "DEBITO", "B", java.math.BigDecimal.ONE, null);
        a.setCard("VISA", "1111", 10, 2026);
        assertTrue(!a.isCardExpiredOn(LocalDate.of(2026, 10, 31)));
        assertTrue(a.isCardExpiredOn(LocalDate.of(2026, 11, 1)));
    }
}
