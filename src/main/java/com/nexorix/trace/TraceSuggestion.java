package com.nexorix.trace;

import com.nexorix.transaction.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Una posible transferencia entre cuentas propias que Trace encontro
 * y que la persona todavia no ha confirmado.
 *
 * amount: dinero que llego a las cuentas de destino.
 * fee:    diferencia que se fue en comisiones (0 si cuadra exacto).
 * key:    identificador estable ("o:12,13|d:20") para confirmarla desde la app o el agente.
 */
public record TraceSuggestion(
        TraceKind kind,
        List<Transaction> origins,
        List<Transaction> destinations,
        BigDecimal amount,
        BigDecimal fee,
        int score,
        String classification
) {

    public String key() {
        return "o:" + ids(origins) + "|d:" + ids(destinations);
    }

    public List<Long> originIds() {
        return origins.stream().map(Transaction::getId).toList();
    }

    public List<Long> destinationIds() {
        return destinations.stream().map(Transaction::getId).toList();
    }

    private static String ids(List<Transaction> list) {
        return list.stream().map(t -> String.valueOf(t.getId())).sorted().collect(Collectors.joining(","));
    }
}
