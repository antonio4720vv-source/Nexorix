package com.nexorix.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que la persona ve antes de confirmar la importacion. */
public record ImportPreview(
        Long batchId,
        String fileName,
        String format,
        Long accountId,
        String accountName,
        String status,
        int total,
        int newRows,
        int duplicates,
        int invalid,
        BigDecimal incomeTotal,
        BigDecimal expenseTotal,
        List<Row> rows,
        boolean aiUsed,
        String checkMessage,
        String errorMessage
) {

    public record Row(
            Long id,
            int line,
            LocalDate date,
            String description,
            BigDecimal amount,
            String type,
            String reference,
            String category,
            String categoryLabel,
            String status,
            String message,
            boolean selected
    ) {
    }
}
