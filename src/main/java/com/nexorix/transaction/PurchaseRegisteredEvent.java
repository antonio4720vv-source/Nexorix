package com.nexorix.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Se publica cuando la persona registra un gasto (egreso) a mano.
 * Lo escucha el modulo de WhatsApp para preguntar que se compro.
 * Las transferencias entre cuentas propias y los extractos importados no lo publican.
 */
public record PurchaseRegisteredEvent(
        Long transactionId,
        Long userId,
        BigDecimal amount,
        String description,
        LocalDateTime transactionDate
) {
}
