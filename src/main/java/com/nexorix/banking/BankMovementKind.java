package com.nexorix.banking;

public enum BankMovementKind {
    /** Compra con tarjeta o datafono (fisica o digital). */
    EXPENSE,
    /** Entre cuentas propias del mismo usuario: nunca dispara el flujo de gasto. */
    INTERNAL_TRANSFER,
    /** Envio de dinero a otra persona. */
    THIRD_PARTY_TRANSFER,
    /** Dinero que entra desde un tercero. */
    INCOME
}
