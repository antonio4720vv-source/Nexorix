package com.nexorix.trace;

import java.math.BigDecimal;

/**
 * Resumen del flujo REAL de dinero de un usuario.
 *
 * grossIncome / grossExpense: lo que dicen los movimientos, sin analisis.
 * internalTransfers: dinero que solo se movio entre cuentas propias (confirmado en Trace).
 * realIncome / realExpense: sin contar las transferencias internas confirmadas.
 *   Las comisiones de las transferencias SI cuentan como gasto real.
 * transferFees: comisiones pagadas en transferencias entre cuentas propias.
 * realNetFlow: realIncome - realExpense.
 * pendingSuggestions / pendingAmount: lo que Trace sugiere y falta confirmar.
 */
public record MoneyFlowSummary(
        BigDecimal grossIncome,
        BigDecimal grossExpense,
        BigDecimal internalTransfers,
        BigDecimal realIncome,
        BigDecimal realExpense,
        BigDecimal realNetFlow,
        int confirmedTransfers,
        int pendingSuggestions,
        BigDecimal pendingAmount,
        BigDecimal transferFees
) {
}
