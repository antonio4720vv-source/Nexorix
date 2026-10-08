package com.nexorix.importer;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lee extractos en CSV (los que exportan bancos y billeteras, o la
 * plantilla de Nexorix).
 *
 * Reconoce solo:
 *  - el separador (; , o tabulador),
 *  - la codificacion (UTF-8 o la de Excel en Windows),
 *  - la fila de encabezados, con nombres en espanol o ingles,
 *  - un monto con signo, o columnas separadas de debito y credito.
 */
public class CsvStatementParser {

    public static final int MAX_ROWS = 5000;

    private static final Set<String> DATE = Set.of(
            "fecha", "date", "fecha transaccion", "fecha movimiento", "fecha operacion",
            "fecha de transaccion", "fecha del movimiento", "dia");
    private static final Set<String> DESCRIPTION = Set.of(
            "descripcion", "concepto", "detalle", "descripcion movimiento", "movimiento",
            "transaccion", "comercio", "establecimiento", "description", "detalle movimiento",
            "nombre", "referencia comercio");
    private static final Set<String> AMOUNT = Set.of(
            "valor", "monto", "importe", "amount", "valor movimiento", "cantidad", "valor transaccion");
    private static final Set<String> DEBIT = Set.of(
            "debito", "debitos", "cargo", "cargos", "retiro", "retiros", "egreso", "egresos",
            "salida", "salidas", "debe", "debit", "valor debito");
    private static final Set<String> CREDIT = Set.of(
            "credito", "creditos", "abono", "abonos", "deposito", "depositos", "ingreso",
            "ingresos", "entrada", "entradas", "haber", "credit", "valor credito");
    private static final Set<String> REFERENCE = Set.of(
            "referencia", "documento", "numero documento", "comprobante", "ref", "nro",
            "numero", "reference", "no documento");
    private static final Set<String> TYPE = Set.of("tipo", "naturaleza", "type", "tipo movimiento");

    /** Resultado: movimientos validos e invalidos, en el orden del archivo. */
    public List<ParsedMovement> parse(byte[] bytes) {

        String text = decode(bytes);
        List<String> lines = text.lines().toList();

        if (lines.size() > MAX_ROWS + 20) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "El archivo tiene demasiadas filas. El máximo es " + MAX_ROWS + ".");
        }

        char delimiter = detectDelimiter(lines);

        // Buscar la fila de encabezados en las primeras 15 filas.
        int headerIndex = -1;
        Columns columns = null;

        for (int i = 0; i < Math.min(15, lines.size()); i++) {
            Columns candidate = Columns.from(split(lines.get(i), delimiter));
            if (candidate.isUsable()) {
                headerIndex = i;
                columns = candidate;
                break;
            }
        }

        if (columns == null) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "No encontramos las columnas de fecha, descripción y valor. "
                            + "Revisa el archivo o usa la plantilla de Nexorix.");
        }

        int defaultYear = LocalDate.now().getYear();
        List<ParsedMovement> result = new ArrayList<>();

        for (int i = headerIndex + 1; i < lines.size(); i++) {

            String raw = lines.get(i);
            if (raw.isBlank()) {
                continue;
            }

            List<String> cells = split(raw, delimiter);
            int lineNumber = i + 1;

            // Filas vacias o de totales al final del archivo.
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            String first = StatementValues.normalizeForMatching(cell(cells, columns.date));
            if (first.startsWith("total") || first.startsWith("saldo")) {
                continue;
            }

            result.add(parseRow(cells, columns, lineNumber, defaultYear, raw));
        }

        return result;
    }

    private ParsedMovement parseRow(List<String> cells, Columns columns, int line,
                                    int defaultYear, String raw) {

        LocalDate date = StatementValues.parseDate(cell(cells, columns.date), defaultYear);
        if (date == null) {
            return ParsedMovement.invalid(line, raw, "La fecha no es válida: \"" + cell(cells, columns.date) + "\".");
        }

        String description = StatementValues.cleanDescription(cell(cells, columns.description));
        if (description.isEmpty()) {
            return ParsedMovement.invalid(line, raw, "Falta la descripción.");
        }

        BigDecimal signed;

        if (columns.amount >= 0) {
            signed = StatementValues.parseAmount(cell(cells, columns.amount));
        } else {
            BigDecimal debit = StatementValues.parseAmount(cell(cells, columns.debit));
            BigDecimal credit = StatementValues.parseAmount(cell(cells, columns.credit));
            boolean hasDebit = debit != null && debit.signum() != 0;
            boolean hasCredit = credit != null && credit.signum() != 0;

            if (hasDebit && hasCredit) {
                return ParsedMovement.invalid(line, raw, "Tiene valor en débito y en crédito a la vez.");
            }
            signed = hasDebit ? debit.abs().negate() : hasCredit ? credit.abs() : null;
        }

        if (signed == null || signed.signum() == 0) {
            return ParsedMovement.invalid(line, raw, "El valor no es válido o es cero.");
        }

        String type = signed.signum() > 0 ? "INGRESO" : "EGRESO";

        // Si hay columna de tipo, manda sobre el signo.
        if (columns.type >= 0) {
            String declared = StatementValues.normalizeForMatching(cell(cells, columns.type));
            if (declared.matches("(ingreso|credito|abono|entrada|c|cr)")) {
                type = "INGRESO";
            } else if (declared.matches("(egreso|debito|cargo|salida|retiro|d|db)")) {
                type = "EGRESO";
            }
        }

        String reference = columns.reference >= 0
                ? StatementValues.cleanDescription(cell(cells, columns.reference)) : "";
        if (reference.length() > 150) {
            reference = reference.substring(0, 150);
        }

        return ParsedMovement.ok(line, date, description, signed.abs(), type,
                reference.isEmpty() ? null : reference, null);
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    /** UTF-8 si es valido; si no, Windows-1252 (Excel en espanol). */
    static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    static char detectDelimiter(List<String> lines) {
        char best = ',';
        int bestCount = 0;
        for (char candidate : new char[]{';', ',', '\t', '|'}) {
            int count = 0;
            for (int i = 0; i < Math.min(10, lines.size()); i++) {
                count += split(lines.get(i), candidate).size() - 1;
            }
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /** Separa una linea respetando las comillas ("Compra, Exito"). */
    static List<String> split(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    /** Posicion de cada columna conocida. -1 = no esta. */
    private static final class Columns {
        int date = -1, description = -1, amount = -1, debit = -1, credit = -1, reference = -1, type = -1;

        static Columns from(List<String> header) {
            Columns columns = new Columns();
            for (int i = 0; i < header.size(); i++) {
                String name = StatementValues.normalizeForMatching(header.get(i));
                if (columns.date < 0 && DATE.contains(name)) columns.date = i;
                else if (columns.description < 0 && DESCRIPTION.contains(name)) columns.description = i;
                else if (columns.amount < 0 && AMOUNT.contains(name)) columns.amount = i;
                else if (columns.debit < 0 && DEBIT.contains(name)) columns.debit = i;
                else if (columns.credit < 0 && CREDIT.contains(name)) columns.credit = i;
                else if (columns.reference < 0 && REFERENCE.contains(name)) columns.reference = i;
                else if (columns.type < 0 && TYPE.contains(name)) columns.type = i;
            }
            return columns;
        }

        boolean isUsable() {
            return date >= 0 && description >= 0
                    && (amount >= 0 || (debit >= 0 && credit >= 0));
        }
    }
}
