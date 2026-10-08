package com.nexorix.trace;

import com.nexorix.account.Account;
import com.nexorix.transaction.Transaction;
import com.nexorix.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Datos de prueba para Trace. */
final class TraceTestData {

    static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 10, 9, 0);
    static final User ANA = user(1L, "ana");

    private TraceTestData() {
    }

    static User user(long id, String username) {
        User user = new User("Usuario " + username, username, username + "@nexorix.co", "1" + id, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    static Account account(long id, String bank) {
        Account account = new Account(bank + " Ana", "AHORROS", bank, BigDecimal.ZERO, ANA);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    static Transaction tx(long id, Account account, String type, long amount, int minutesAfterBase) {
        Transaction t = new Transaction(BigDecimal.valueOf(amount), type, type + " " + id,
                BASE.plusMinutes(minutesAfterBase), account, null);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    static Transaction out(long id, Account account, long amount, int minutes) {
        return tx(id, account, "EGRESO", amount, minutes);
    }

    static Transaction in(long id, Account account, long amount, int minutes) {
        return tx(id, account, "INGRESO", amount, minutes);
    }
}
