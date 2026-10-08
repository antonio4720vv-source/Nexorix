package com.nexorix.importer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee extractos bancarios en PDF (con texto, no escaneados).
 *
 * Cada banco dibuja su extracto distinto, asi que este lector es
 * generico: busca lineas que EMPIEZAN con una fecha y TERMINAN con
 * uno o mas valores. Para saber si el dinero entro o salio usa, en
 * este orden:
 *   1. un signo menos o parentesis en el valor,
 *   2. el saldo de la linea comparado con el saldo anterior,
 *   3. palabras como "compra", "retiro", "abono", "recibido".
 */
public class PdfStatementParser {

    public static final int MAX_PAGES = 60;

    private static final String MONTH_WORDS =
            "ene|feb|mar|abr|may|jun|jul|ago|sep|set|oct|nov|dic|jan|apr|aug|dec";

    /** Fecha al inicio de la linea. */
    private static final Pattern LEADING_DATE = Pattern.compile(
            "^(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}"
                    + "|\\d{1,2}[-/.]\\d{1,2}(?:[-/.]\\d{2,4})?"
                    + "|\\d{1,2}[\\s/-]+(?:" + MONTH_WORDS + ")[a-z]*\\.?(?:[\\s/-]+\\d{2,4})?)\\s+",
            Pattern.CASE_INSENSITIVE);

    /** Un valor de dinero: $ -1.234.567,89  +$20.000,00  (50.000)  50.000- */
    private static final String MONEY =
            "\\(?[+-]?\\s?\\$?\\s?[+-]?\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?\\)?-?"
                    + "|\\(?[+-]?\\s?\\$?\\s?[+-]?\\d+[.,]\\d{2}\\)?-?";

    /**
     * Lineas de resumen que tienen valores pero NO son movimientos
     * ("Total recibido", "Saldo final", "Pagina 2"...).
     */
    private static final List<String> SUMMARY_WORDS = List.of(
            "saldo", "total", "subtotal", "resumen", "pagina", "periodo", "corte", "cupo",
            "fecha", "valor", "descripcion");

    /** Uno o mas valores al final de la linea. */
    private static final Pattern TRAILING_AMOUNTS = Pattern.compile(
            "((?:\\s+(?:" + MONEY + "))+)\\s*$");

    private static final Pattern ONE_AMOUNT = Pattern.compile(MONEY);

    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");

    /** Se revisan primero: "PAGO NOMINA" es un ingreso aunque diga "pago". */
    private static final List<String> INCOME_WORDS = List.of(
            "nomina", "salario", "abono", "consignacion", "deposito", "recibido", "recibiste",
            "transferencia de", "intereses", "reintegro", "devolucion", "rendimientos");

    private static final List<String> EXPENSE_WORDS = List.of(
            "compra", "pago", "retiro", "debito", "cargo", "enviado", "enviaste", "envio", "transferencia a",
            "comision", "cuota", "impuesto", "gmf", "4x1000", "avance", "pse");

    // ============================================================
    // PDF -> TEXTO
    // ============================================================

    public List<ParsedMovement> parse(byte[] bytes, String password) {

        List<ParsedMovement> movements = parseText(extractText(bytes, password));

        if (movements.isEmpty()) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "No encontramos movimientos en este PDF. Si tu banco permite descargar el "
                            + "extracto en CSV o Excel, prueba con ese formato.");
        }

        return movements;
    }

    /** Mensaje cuando el PDF no tiene texto (escaneado o foto). */
    public static final String NO_TEXT_MESSAGE =
            "Este PDF parece una imagen escaneada y no tiene texto.";

    /** Saca el texto del PDF. Lanza ImportException si tiene contrasena o no tiene texto. */
    public String extractText(byte[] bytes, String password) {

        String text;

        try (PDDocument document = Loader.loadPDF(bytes, password == null ? "" : password)) {

            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new ImportException(ImportException.INVALID_FILE,
                        "El PDF tiene más de " + MAX_PAGES + " páginas.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);

        } catch (InvalidPasswordException exception) {
            boolean triedPassword = password != null && !password.isBlank();
            throw new ImportException(
                    triedPassword ? ImportException.PASSWORD_INVALID : ImportException.PASSWORD_REQUIRED,
                    triedPassword
                            ? "La contraseña del PDF no es correcta."
                            : "Este PDF tiene contraseña. En muchos bancos es tu número de cédula.");
        } catch (IOException exception) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "No pudimos abrir el PDF. Verifica que sea el extracto original del banco.");
        }

        if (text == null || text.replaceAll("\\s", "").length() < 20) {
            throw new ImportException(ImportException.INVALID_FILE, NO_TEXT_MESSAGE);
        }

        return text;
    }

    /**
     * Devuelve el PDF sin contrasena, para que la IA pueda leerlo.
     * Si no tiene contrasena, devuelve los mismos bytes.
     */
    public byte[] withoutPassword(byte[] bytes, String password) {
        if (password == null || password.isBlank()) {
            return bytes;
        }
        try (PDDocument document = Loader.loadPDF(bytes, password)) {
            if (!document.isEncrypted()) {
                return bytes;
            }
            document.setAllSecurityToBeRemoved(true);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new ImportException(ImportException.INVALID_FILE, "No pudimos abrir el PDF.");
        }
    }

    // ============================================================
    // TOTALES DEL EXTRACTO (para comprobar que se leyo completo)
    // ============================================================

    /** Totales que el banco imprime en el resumen. Cualquiera puede ser null. */
    public record Totals(BigDecimal credits, BigDecimal debits) {
        public boolean isEmpty() {
            return credits == null && debits == null;
        }
    }

    private static final Pattern CREDIT_TOTAL = Pattern.compile(
            "total\\s+(?:de\\s+)?(?:abonos|creditos|depositos|entradas|ingresos|recibido)\\s*:?\\s*("
                    + "\\(?[+-]?\\s?\\$?\\s?[+-]?\\d[\\d.,]*)");
    private static final Pattern DEBIT_TOTAL = Pattern.compile(
            "total\\s+(?:de\\s+)?(?:cargos|debitos|retiros|salidas|egresos|enviado|gastado)\\s*:?\\s*("
                    + "\\(?[+-]?\\s?\\$?\\s?[+-]?\\d[\\d.,]*)");

    public static Totals findTotals(String text) {
        if (text == null) {
            return new Totals(null, null);
        }
        String normalized = StatementValues.stripAccents(text.toLowerCase(java.util.Locale.ROOT));
        return new Totals(firstTotal(CREDIT_TOTAL, normalized), firstTotal(DEBIT_TOTAL, normalized));
    }

    private static BigDecimal firstTotal(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            BigDecimal value = StatementValues.parseAmount(matcher.group(1));
            return value == null ? null : value.abs();
        }
        return null;
    }

    // ============================================================
    // TEXTO -> MOVIMIENTOS
    // ============================================================

    /** Una linea que parece movimiento, antes de decidir si entro o salio. */
    private record Candidate(
            int line,
            LocalDate date,
            String description,
            String normalized,
            BigDecimal amount,
            BigDecimal balance,
            String rawAmount
    ) {
        boolean explicitIncome() {
            return rawAmount.contains("+");
        }

        boolean explicitExpense() {
            return amount.signum() < 0 || rawAmount.contains("(");
        }
    }

    /** Separado del PDF para poder probarlo con texto. */
    public List<ParsedMovement> parseText(String text) {

        int defaultYear = mostCommonYear(text);
        List<Candidate> candidates = new ArrayList<>();
        BigDecimal openingBalance = null;
        LocalDate lastDate = null;
        String[] lines = text.split("\\r?\\n");

        // ------------------------------------------------------------
        // Paso 1: encontrar las lineas que son movimientos.
        // ------------------------------------------------------------

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i].replace('\u00A0', ' ').trim();
            if (line.isEmpty()) {
                continue;
            }

            String normalized = StatementValues.normalizeForMatching(line);

            // "Saldo anterior $988,456.85  Saldo promedio $190,870.41": el primer valor.
            if (normalized.startsWith("saldo anterior") || normalized.startsWith("saldo inicial")) {
                List<BigDecimal> amounts = amounts(line);
                if (!amounts.isEmpty() && openingBalance == null) {
                    openingBalance = amounts.get(0);
                }
                continue;
            }

            Matcher dateMatcher = LEADING_DATE.matcher(line);
            boolean hasDate = dateMatcher.find();
            String rest = hasDate ? line.substring(dateMatcher.end()) : line;

            Matcher amountsMatcher = TRAILING_AMOUNTS.matcher(rest);
            if (!amountsMatcher.find()) {
                continue;
            }

            LocalDate date;

            if (hasDate) {
                date = StatementValues.parseDate(dateMatcher.group(1), defaultYear);
                if (date == null) {
                    continue;
                }
            } else {
                // Linea SIN fecha pero con descripcion y valor: es un cobro del mismo
                // dia que el movimiento anterior. Ejemplo (Nu):
                //   02 ago  Enviaste a Juan          -$39.800,00
                //           Impuesto del 4x1000         -$159,20
                if (lastDate == null || isSummaryLine(normalized)) {
                    continue;
                }
                date = lastDate;
            }

            String description = StatementValues.cleanDescription(rest.substring(0, amountsMatcher.start()));
            if (description.isEmpty()) {
                continue;
            }

            List<BigDecimal> values = amounts(amountsMatcher.group(1));
            BigDecimal amount = values.get(0);
            if (amount.signum() == 0) {
                continue;
            }

            BigDecimal balance = values.size() >= 2 ? values.get(values.size() - 1) : null;

            candidates.add(new Candidate(i + 1, date, description, normalized, amount, balance,
                    firstAmountText(amountsMatcher.group(1))));

            if (hasDate) {
                lastDate = date;
            }
        }

        // ------------------------------------------------------------
        // Paso 2: decidir si cada movimiento entro o salio.
        // ------------------------------------------------------------

        // ¿El banco marca las salidas con signo menos y deja las entradas sin signo?
        // (Bancolombia: "$-43,000.00" para cargos y "$500,000.00" para abonos.)
        long negatives = candidates.stream().filter(Candidate::explicitExpense).count();
        boolean anyPlus = candidates.stream().anyMatch(Candidate::explicitIncome);
        boolean unsignedMeansIncome = !anyPlus && !candidates.isEmpty()
                && negatives * 10 >= candidates.size() * 3; // 30 % o mas con signo menos

        List<ParsedMovement> result = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {

            Candidate current = candidates.get(i);
            String type = null;
            String warning = null;

            // 1. Signo explicito: "+$20.000" entro, "-$39.800" o "(12.500)" salio.
            if (current.explicitIncome()) {
                type = "INGRESO";
            } else if (current.explicitExpense()) {
                type = "EGRESO";
            }

            // 2. Saldo. Funciona con extractos en orden normal (lo mas viejo primero)
            //    y en orden inverso (lo mas reciente primero, como Bancolombia).
            if (type == null && current.balance() != null) {
                BigDecimal before = i > 0 ? candidates.get(i - 1).balance() : openingBalance;
                type = typeFromBalances(before, current.amount().abs(), current.balance());

                if (type == null && i + 1 < candidates.size()) {
                    type = typeFromBalances(candidates.get(i + 1).balance(),
                            current.amount().abs(), current.balance());
                }
            }

            // 3. Convencion del extracto: si las salidas traen "-", lo que no lo trae entro.
            if (type == null && unsignedMeansIncome) {
                type = "INGRESO";
            }

            // 4. Palabras.
            if (type == null) {
                type = typeFromWords(current.normalized());
            }

            if (type == null) {
                type = "EGRESO";
                warning = "No estamos seguros de si entró o salió dinero. Revísalo.";
            }

            result.add(ParsedMovement.ok(current.line(), current.date(), current.description(),
                    current.amount().abs(), type, null, warning));
        }

        return result;
    }

    /** saldo anterior + valor = saldo -> INGRESO; saldo anterior - valor = saldo -> EGRESO. */
    static String typeFromBalances(BigDecimal before, BigDecimal amount, BigDecimal after) {
        if (before == null || after == null) {
            return null;
        }
        if (before.add(amount).subtract(after).abs().compareTo(BigDecimal.ONE) < 0) {
            return "INGRESO";
        }
        if (before.subtract(amount).subtract(after).abs().compareTo(BigDecimal.ONE) < 0) {
            return "EGRESO";
        }
        return null;
    }

    static boolean isSummaryLine(String normalizedLine) {
        for (String word : SUMMARY_WORDS) {
            if (normalizedLine.startsWith(word) || normalizedLine.contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    static String typeFromWords(String normalizedLine) {
        // " palabra" encuentra tambien plurales: "comision" -> "comisiones".
        String text = " " + normalizedLine + " ";
        for (String word : INCOME_WORDS) {
            if (text.contains(" " + word)) {
                return "INGRESO";
            }
        }
        for (String word : EXPENSE_WORDS) {
            if (text.contains(" " + word)) {
                return "EGRESO";
            }
        }
        return null;
    }

    private static List<BigDecimal> amounts(String text) {
        List<BigDecimal> values = new ArrayList<>();
        Matcher matcher = ONE_AMOUNT.matcher(text);
        while (matcher.find()) {
            BigDecimal value = StatementValues.parseAmount(matcher.group());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static String firstAmountText(String text) {
        Matcher matcher = ONE_AMOUNT.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    /** El ano que mas aparece en el extracto (para fechas sin ano). */
    static int mostCommonYear(String text) {
        Map<Integer, Integer> counts = new HashMap<>();
        Matcher matcher = YEAR.matcher(text);
        int currentYear = LocalDate.now().getYear();
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year <= currentYear) {
                counts.merge(year, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(currentYear);
    }
}
