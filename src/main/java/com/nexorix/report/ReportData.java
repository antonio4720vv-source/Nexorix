package com.nexorix.report;

import com.nexorix.trace.MoneyFlowSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Todo lo que necesita un reporte conciliado. */
public record ReportData(
        String personName,
        String documentNumber,
        LocalDate from,
        LocalDate to,
        MoneyFlowSummary summary,
        Map<String, BigDecimal> realExpensesByCategory,
        Map<String, BigDecimal> realIncomesByCategory,
        List<MonthRow> months,
        List<Row> rows
) {

    public record MonthRow(String month, BigDecimal realIncome, BigDecimal realExpense) {
    }

    /** Un movimiento con su parte real y su parte interna. */
    public record Row(
            LocalDate date,
            String bank,
            String account,
            String type,
            String description,
            String category,
            BigDecimal amount,
            BigDecimal internalPart,
            BigDecimal realPart,
            String reference
    ) {
    }
}
