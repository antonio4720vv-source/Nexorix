package com.nexorix.importer;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizacion de los valores que vienen en los extractos colombianos:
 * montos con puntos o comas, fechas en muchos formatos y descripciones.
 */
public final class StatementValues {

    private StatementValues() {
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("jan", 1),
            Map.entry("feb", 2),
            Map.entry("mar", 3),
            Map.entry("abr", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("jun", 6),
            Map.entry("jul", 7),
            Map.entry("ago", 8), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("set", 9),
            Map.entry("oct", 10),
            Map.entry("nov", 11),
            Map.entry("dic", 12), Map.entry("dec", 12)
    );

    private static final Pattern ISO = Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})(?:[-/.](\\d{2,4}))?$");
    private static final Pattern TEXT_MONTH = Pattern.compile(
            "^(\\d{1,2})[\\s/-]*([a-z]{3})[a-z]*\\.?(?:[\\s/-]*(\\d{2,4}))?$");

    // ============================================================
    // MONTOS
    // ============================================================

    /**
     * Convierte un monto escrito como en Colombia a numero, con su signo.
     * Acepta: "1.234.567", "1.234.567,89", "1,234,567.89", "$ -50.000",
     * "(50.000)", "50.000-". Devuelve null si no es un monto.
     */
    public static BigDecimal parseAmount(String raw) {

        if (raw == null) {
            return null;
        }

        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        boolean negative = text.contains("-") || (text.startsWith("(") && text.endsWith(")"));

        String digits = text.replaceAll("[^0-9.,]", "");
        if (digits.isEmpty() || !digits.matches(".*\\d.*")) {
            return null;
        }

        int lastDot = digits.lastIndexOf('.');
        int lastComma = digits.lastIndexOf(',');
        String normalized;

        if (lastDot >= 0 && lastComma >= 0) {
            // Los dos: el ultimo es el separador de decimales.
            char decimal = lastDot > lastComma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            normalized = digits.replace(String.valueOf(thousands), "").replace(decimal, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char separator = lastDot >= 0 ? '.' : ',';
            int count = digits.length() - digits.replace(String.valueOf(separator), "").length();
            int decimals = digits.length() - digits.lastIndexOf(separator) - 1;

            if (count == 1 && decimals >= 1 && decimals <= 2) {
                normalized = digits.replace(separator, '.');          // 1500,50 -> decimal
            } else {
                normalized = digits.replace(String.valueOf(separator), ""); // 1.500.000 -> miles
            }
        } else {
            normalized = digits;
        }

        try {
            BigDecimal value = new BigDecimal(normalized);
            return negative ? value.negate() : value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // ============================================================
    // FECHAS
    // ============================================================

    /**
     * Lee fechas como 2026-10-03, 03/10/2026, 3-10-26, 03/10 (sin ano),
     * "03 oct 2026" o "3 OCT". defaultYear se usa cuando la fecha no
     * trae el ano. Devuelve null si no es una fecha valida.
     */
    public static LocalDate parseDate(String raw, int defaultYear) {

        if (raw == null) {
            return null;
        }

        String text = stripAccents(raw.trim().toLowerCase(Locale.ROOT));
        if (text.isEmpty()) {
            return null;
        }

        try {
            Matcher iso = ISO.matcher(text);
            if (iso.matches()) {
                return valid(LocalDate.of(Integer.parseInt(iso.group(1)),
                        Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3))));
            }

            Matcher dmy = DMY.matcher(text);
            if (dmy.matches()) {
                int year = dmy.group(3) == null ? defaultYear : fullYear(dmy.group(3));
                return valid(LocalDate.of(year,
                        Integer.parseInt(dmy.group(2)), Integer.parseInt(dmy.group(1))));
            }

            Matcher textMonth = TEXT_MONTH.matcher(text);
            if (textMonth.matches()) {
                Integer month = MONTHS.get(textMonth.group(2));
                if (month == null) {
                    return null;
                }
                int year = textMonth.group(3) == null ? defaultYear : fullYear(textMonth.group(3));
                return valid(LocalDate.of(year, month, Integer.parseInt(textMonth.group(1))));
            }
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }

        return null;
    }

    private static int fullYear(String year) {
        int value = Integer.parseInt(year);
        return value < 100 ? 2000 + value : value;
    }

    /** Fechas razonables: desde el ano 2000 hasta manana. */
    private static LocalDate valid(LocalDate date) {
        if (date.getYear() < 2000 || date.isAfter(LocalDate.now().plusDays(1))) {
            return null;
        }
        return date;
    }

    // ============================================================
    // TEXTO
    // ============================================================

    /** Quita caracteres raros, espacios repetidos y limita a 255 caracteres. */
    public static String cleanDescription(String raw) {

        if (raw == null) {
            return "";
        }

        String text = raw
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", " ")
                .replaceAll("[<>]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("^[\\s\\-|:;*]+|[\\s\\-|:;*]+$", "");

        return text.length() > 255 ? text.substring(0, 255).trim() : text;
    }

    /** Texto en minusculas, sin tildes y solo letras/numeros, para comparar. */
    public static String normalizeForMatching(String raw) {
        if (raw == null) {
            return "";
        }
        return stripAccents(raw.toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public static String stripAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
