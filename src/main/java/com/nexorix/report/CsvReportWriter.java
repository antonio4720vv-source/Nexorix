package com.nexorix.report;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * CSV que Excel en espanol abre bien: separador ";" y marca UTF-8 (tildes correctas).
 * Protegido contra "inyeccion de formulas": un texto que empieza con = + - @ se antepone con '.
 */
public final class CsvReportWriter {

    private CsvReportWriter() {
    }

    public static byte[] write(ReportData data) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Fecha;Banco;Cuenta;Tipo;Descripción;Categoría;Valor;Parte transferencia interna;Parte real;Referencia\n");
        for (ReportData.Row r : data.rows()) {
            csv.append(r.date()).append(';')
                    .append(cell(r.bank())).append(';')
                    .append(cell(r.account())).append(';')
                    .append(r.type()).append(';')
                    .append(cell(r.description())).append(';')
                    .append(cell(r.category())).append(';')
                    .append(number(r.amount())).append(';')
                    .append(number(r.internalPart())).append(';')
                    .append(number(r.realPart())).append(';')
                    .append(cell(r.reference())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 1234567.5 -> "1234567,50" (coma decimal, como Excel en Colombia). */
    public static String number(BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String cell(String value) {
        if (value == null) return "";
        String text = value.replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@\t".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
