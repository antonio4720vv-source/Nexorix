package com.nexorix.trace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una transferencia conciliada (o deshecha), lista para mostrar en el historial. */
public record TraceGroupView(
        String groupId,
        String kind,
        String kindLabel,
        boolean active,
        int score,
        String classification,
        BigDecimal amount,
        BigDecimal fee,
        LocalDateTime createdAt,
        LocalDateTime undoneAt,
        List<TraceMovementView> origins,
        List<TraceMovementView> destinations
) {
}
