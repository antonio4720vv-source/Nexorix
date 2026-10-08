package com.nexorix.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un movimiento leido de un extracto, ya normalizado.
 *
 * amount siempre es positivo; el sentido lo da type (INGRESO o EGRESO).
 * Si error no es null, la linea no se pudo leer bien y no se importa.
 * warning es un aviso que no impide importar (por ejemplo, tipo dudoso).
 */
public record ParsedMovement(
        int line,
        LocalDate date,
        String description,
        BigDecimal amount,
        String type,
        String reference,
        String error,
        String warning
) {

    public static ParsedMovement ok(int line, LocalDate date, String description,
                                    BigDecimal amount, String type, String reference, String warning) {
        return new ParsedMovement(line, date, description, amount, type, reference, null, warning);
    }

    public static ParsedMovement invalid(int line, String rawText, String error) {
        String text = StatementValues.cleanDescription(rawText);
        return new ParsedMovement(line, null, text.isEmpty() ? "(línea vacía)" : text,
                null, null, null, error, null);
    }

    public boolean isValid() {
        return error == null;
    }
}
