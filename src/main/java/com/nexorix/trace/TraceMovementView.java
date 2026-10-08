package com.nexorix.trace;

import com.nexorix.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un movimiento dentro de una transferencia de Trace. */
public record TraceMovementView(
        Long id,
        String bank,
        String accountName,
        String description,
        BigDecimal amount,
        LocalDateTime date
) {
    public static TraceMovementView of(Transaction t) {
        return new TraceMovementView(t.getId(), t.getAccount().getBank(), t.getAccount().getName(),
                t.getDescription(), t.getAmount(), t.getTransactionDate());
    }
}
