package com.nexorix.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountBalanceTest {

    private static Account account(String type, String balance, String limit) {
        Account a = new Account("Cuenta", type, "Banco", new BigDecimal(balance), null);
        if (limit != null) a.setCreditLimit(new BigDecimal(limit));
        return a;
    }

    @Test
    void elSaldoNuncaBajaDeCero() {
        Account a = account("AHORROS", "100.00", null);
        a.applyClamped(new BigDecimal("-500"));
        assertEquals(0, a.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void laTarjetaDeCreditoNoPasaDelCupoNiBajaDeCero() {
        Account a = account("CREDITO", "1000.00", "2000.00");
        a.applyClamped(new BigDecimal("5000"));
        assertEquals(0, a.getBalance().compareTo(new BigDecimal("2000")));
        a.applyClamped(new BigDecimal("-9000"));
        assertEquals(0, a.getBalance().compareTo(BigDecimal.ZERO));
    }
}
