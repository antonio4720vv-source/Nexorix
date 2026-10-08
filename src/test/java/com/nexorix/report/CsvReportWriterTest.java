package com.nexorix.report;

import com.nexorix.trace.MoneyFlowSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReportWriterTest {

    private static ReportData data(List<ReportData.Row> rows) {
        BigDecimal z = BigDecimal.ZERO;
        return new ReportData("Ana", "1000000008", null, null,
                new MoneyFlowSummary(z, z, z, z, z, z, 0, 0, z, z), Map.of(), Map.of(), List.of(), rows);
    }

    @Test
    void escribeUnCsvQueExcelEnEspanolEntiende() {
        byte[] file = CsvReportWriter.write(data(List.of(new ReportData.Row(LocalDate.of(2026, 9, 1), "Nequi",
                "Nequi Ana", "INGRESO", "Salario; septiembre", "Salario",
                new BigDecimal("3000000"), BigDecimal.ZERO, new BigDecimal("3000000"), null))));

        String text = new String(file, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿");                 // tildes correctas en Excel
        assertThat(text).contains("Descripción;Categoría");
        assertThat(text).contains("\"Salario; septiembre\"");   // el ; dentro del texto no rompe columnas
        assertThat(text).contains("3000000,00");               // coma decimal
    }

    @Test
    void evitaInyeccionDeFormulas() {
        assertThat(CsvReportWriter.cell("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(CsvReportWriter.cell("+57 300")).isEqualTo("'+57 300");
        assertThat(CsvReportWriter.cell("Compra Exito")).isEqualTo("Compra Exito");
    }

    @Test
    void elPdfSeGeneraYEsUnPdfValido() {
        byte[] pdf = PdfReportWriter.write(data(List.of(new ReportData.Row(LocalDate.of(2026, 9, 1), "Nequi",
                "Nequi Ana", "INGRESO", "Pago nómina → empresa", "Salario",
                new BigDecimal("3000000"), BigDecimal.ZERO, new BigDecimal("3000000"), null))));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void elDocumentoSeMuestraEnmascarado() {
        assertThat(PdfReportWriter.mask("1000000008")).isEqualTo("****0008");
        assertThat(PdfReportWriter.money(new BigDecimal("-1500000"))).isEqualTo("-$ 1.500.000");
    }
}
