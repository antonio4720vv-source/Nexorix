package com.nexorix.trace;

/** Forma de una transferencia entre cuentas propias. */
public enum TraceKind {

    /** Una salida -> una entrada. */
    ONE_TO_ONE("1 → 1"),

    /** Una salida que se reparte en varias entradas (ej: Nequi -> Nu + Davivienda). */
    ONE_TO_MANY("1 → N"),

    /** Varias salidas que llegan juntas a una entrada (ej: dos envios que suman un abono). */
    MANY_TO_ONE("N → 1");

    private final String label;

    TraceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
