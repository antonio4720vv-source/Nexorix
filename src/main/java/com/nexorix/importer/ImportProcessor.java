package com.nexorix.importer;

import com.nexorix.ai.AiClassifier;
import com.nexorix.ai.AiException;
import com.nexorix.ai.AiStatementReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee UN archivo en segundo plano. Muchas personas pueden subir archivos
 * a la vez: cada archivo espera su turno en la cola y se procesa aqui.
 *
 * Importante para aguantar carga: las llamadas a la IA (que tardan
 * segundos) se hacen FUERA de las transacciones, asi no se quedan
 * ocupadas las conexiones a la base de datos.
 */
@Component
public class ImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final ImportService importService;
    private final AiStatementReader aiReader;
    private final AiClassifier aiClassifier;
    private final TransactionTemplate tx;
    private final ImportAudit audit;
    private final CsvStatementParser csvParser = new CsvStatementParser();
    private final PdfStatementParser pdfParser = new PdfStatementParser();

    public ImportProcessor(
            ImportBatchRepository batchRepository,
            ImportRowRepository rowRepository,
            ImportService importService,
            AiStatementReader aiReader,
            AiClassifier aiClassifier,
            TransactionTemplate tx,
            ImportAudit audit
    ) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.importService = importService;
        this.aiReader = aiReader;
        this.aiClassifier = aiClassifier;
        this.tx = tx;
        this.audit = audit;
    }

    /** Resultado de leer el archivo, antes de guardarlo. */
    record Analysis(List<ParsedMovement> movements, boolean aiUsed, String checkMessage) {
    }

    // ============================================================
    // PROCESO COMPLETO DE UN ARCHIVO
    // ============================================================

    public void process(Long batchId, Path file, String format, String password) {
        try {
            String status = tx.execute(s -> batchRepository.findById(batchId).map(batch -> {
                if (!ImportBatch.QUEUED.equals(batch.getStatus())) {
                    return batch.getStatus();
                }
                batch.markProcessing();
                audit.recordAfterCommit(batch, ImportAudit.PROCESSING, "Empezó la lectura");
                return ImportBatch.PROCESSING;
            }).orElse("MISSING"));

            if (!ImportBatch.PROCESSING.equals(status)) {
                return; // cancelado o borrado mientras esperaba
            }

            long started = System.nanoTime();
            byte[] bytes = Files.readAllBytes(file);
            Analysis analysis = analyze(format, bytes, password);
            Map<Integer, String> aiCategories = classifyWithAi(analysis.movements());

            tx.executeWithoutResult(s -> {
                ImportBatch batch = batchRepository.findById(batchId).orElseThrow();
                List<ImportRow> rows = importService.buildRows(batch, batch.getAccount(), analysis.movements());

                // La IA solo mejora la categoria de lo que quedo en "Otros".
                int recategorized = 0;
                for (int i = 0; i < rows.size(); i++) {
                    String better = aiCategories.get(i);
                    if (better != null && !ImportRow.INVALID.equals(rows.get(i).getStatus())) {
                        rows.get(i).setCategory(better);
                        recategorized++;
                    }
                }

                int news = 0, duplicates = 0, invalid = 0;
                for (ImportRow row : rows) {
                    switch (row.getStatus()) {
                        case ImportRow.NEW -> news++;
                        case ImportRow.DUPLICATE -> duplicates++;
                        default -> invalid++;
                    }
                }

                rowRepository.saveAll(rows);
                batch.markPreview(rows.size(), news, duplicates, invalid, analysis.aiUsed(), analysis.checkMessage());
                batchRepository.save(batch);

                String method = "CSV".equals(format) ? "CSV" : analysis.aiUsed() ? "PDF leído con IA" : "PDF leído con reglas";
                audit.recordAfterCommit(batch, ImportAudit.READ, method + " · " + rows.size() + " movimientos ("
                        + news + " nuevos, " + duplicates + " repetidos, " + invalid + " con problemas)"
                        + (analysis.checkMessage() == null ? "" : " · " + analysis.checkMessage())
                        + " · " + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9) + " s");
                if (recategorized > 0) {
                    audit.recordAfterCommit(batch, ImportAudit.AI_CATEGORIES,
                            "La IA mejoró la categoría de " + recategorized + " movimientos");
                }
            });

            log.info("Archivo {} listo para revisar (IA: {})", batchId, analysis.aiUsed());

        } catch (ImportException exception) {
            fail(batchId, exception.getMessage());
        } catch (Exception exception) {
            log.error("Error procesando el archivo {}", batchId, exception);
            fail(batchId, "No pudimos leer este archivo. Intenta de nuevo o súbelo en CSV.");
        } finally {
            try {
                Files.deleteIfExists(file); // el archivo no se guarda: privacidad
            } catch (IOException ignored) {
                // lo borra la limpieza periodica
            }
        }
    }

    private void fail(Long batchId, String message) {
        tx.executeWithoutResult(s -> batchRepository.findById(batchId).ifPresent(batch -> {
            batch.fail(message);
            batchRepository.save(batch);
            audit.recordAfterCommit(batch, ImportAudit.FAILED, message);
        }));
    }

    // ============================================================
    // LECTURA: REGLAS PRIMERO, IA CUANDO HACE FALTA
    // ============================================================

    Analysis analyze(String format, byte[] bytes, String password) {

        if ("CSV".equals(format)) {
            return new Analysis(csvParser.parse(bytes), false, null);
        }

        // 1. Texto del PDF (si es escaneado no hay texto: la IA lo puede leer).
        String text = null;
        try {
            text = pdfParser.extractText(bytes, password);
        } catch (ImportException exception) {
            if (!PdfStatementParser.NO_TEXT_MESSAGE.equals(exception.getMessage())) {
                throw exception; // contrasena, PDF danado...
            }
        }

        List<ParsedMovement> rules = text == null ? List.of() : pdfParser.parseText(text);
        PdfStatementParser.Totals totals = PdfStatementParser.findTotals(text);

        boolean rulesMatch = matches(rules, totals);
        boolean needAi = needsAi(rules, totals, rulesMatch);

        if (!needAi) {
            return new Analysis(rules, false, checkMessage(rules, totals));
        }

        if (!aiReader.isEnabled()) {
            if (validCount(rules) == 0) {
                throw new ImportException(ImportException.INVALID_FILE, text == null
                        ? PdfStatementParser.NO_TEXT_MESSAGE + " Activa la IA de Nexorix o súbelo en CSV."
                        : "No encontramos movimientos en este PDF. Prueba con el CSV de tu banco.");
            }
            return new Analysis(rules, false, checkMessage(rules, totals));
        }

        // 2. La IA lee el PDF completo.
        try {
            AiStatementReader.AiReading reading = aiReader.read(pdfParser.withoutPassword(bytes, password));
            PdfStatementParser.Totals aiTotals = totals.isEmpty()
                    ? new PdfStatementParser.Totals(reading.totalCredits(), reading.totalDebits())
                    : totals;

            boolean aiMatch = matches(reading.movements(), aiTotals);
            boolean useAi = validCount(reading.movements()) > 0
                    && (validCount(rules) == 0 || (aiMatch && !rulesMatch) || (aiTotals.isEmpty() && !rulesMatch
                    && validCount(reading.movements()) >= validCount(rules)));

            if (useAi) {
                return new Analysis(reading.movements(), true, checkMessage(reading.movements(), aiTotals));
            }
        } catch (AiException exception) {
            log.warn("Lectura con IA no disponible: {}", exception.getMessage());
            if (validCount(rules) == 0) {
                throw new ImportException(ImportException.INVALID_FILE,
                        "No pudimos leer este PDF: " + exception.getMessage());
            }
        }

        return new Analysis(rules, false, checkMessage(rules, totals));
    }

    /** Se pide ayuda a la IA si las reglas no encontraron nada, no cuadran o dudan mucho. */
    static boolean needsAi(List<ParsedMovement> rules, PdfStatementParser.Totals totals, boolean rulesMatch) {
        int valid = validCount(rules);
        if (valid == 0) {
            return true;
        }
        if (!totals.isEmpty() && !rulesMatch) {
            return true;
        }
        long invalid = rules.size() - valid;
        long doubtful = rules.stream().filter(m -> m.isValid() && m.warning() != null).count();
        return invalid * 10 > rules.size() * 3 || doubtful * 10 > valid * 3;
    }

    /** ¿Las sumas de lo leido coinciden con los totales del banco? (tolerancia de $1) */
    static boolean matches(List<ParsedMovement> movements, PdfStatementParser.Totals totals) {
        if (totals == null || totals.isEmpty()) {
            return false;
        }
        BigDecimal[] sums = sums(movements);
        boolean credits = totals.credits() == null || totals.credits().subtract(sums[0]).abs().compareTo(BigDecimal.ONE) <= 0;
        boolean debits = totals.debits() == null || totals.debits().subtract(sums[1]).abs().compareTo(BigDecimal.ONE) <= 0;
        return credits && debits;
    }

    static String checkMessage(List<ParsedMovement> movements, PdfStatementParser.Totals totals) {
        if (totals == null || totals.isEmpty()) {
            return null;
        }
        BigDecimal[] sums = sums(movements);
        if (matches(movements, totals)) {
            return "Cuadra con los totales del extracto: entradas " + money(sums[0]) + " y salidas " + money(sums[1]) + ".";
        }
        List<String> parts = new ArrayList<>();
        if (totals.credits() != null) parts.add("entradas " + money(totals.credits()) + " (leímos " + money(sums[0]) + ")");
        if (totals.debits() != null) parts.add("salidas " + money(totals.debits()) + " (leímos " + money(sums[1]) + ")");
        return "No cuadra con el extracto: " + String.join(", ", parts) + ". Revisa antes de importar.";
    }

    private static BigDecimal[] sums(List<ParsedMovement> movements) {
        BigDecimal credits = BigDecimal.ZERO, debits = BigDecimal.ZERO;
        for (ParsedMovement m : movements) {
            if (!m.isValid()) continue;
            if ("INGRESO".equals(m.type())) credits = credits.add(m.amount());
            else debits = debits.add(m.amount());
        }
        return new BigDecimal[]{credits, debits};
    }

    private static int validCount(List<ParsedMovement> movements) {
        return (int) movements.stream().filter(ParsedMovement::isValid).count();
    }

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** Indices (posicion en la lista) de lo que las reglas dejaron en "Otros" -> categoria de la IA. */
    private Map<Integer, String> classifyWithAi(List<ParsedMovement> movements) {
        List<AiClassifier.Item> pending = new ArrayList<>();
        for (int i = 0; i < movements.size(); i++) {
            ParsedMovement m = movements.get(i);
            if (!m.isValid()) continue;
            String category = TransactionClassifier.classify(m.description(), m.type());
            if (category.equals(TransactionClassifier.OTHER_EXPENSE) || category.equals(TransactionClassifier.OTHER_INCOME)) {
                pending.add(new AiClassifier.Item(i, m.description(), m.type()));
            }
        }
        return aiClassifier.classify(pending);
    }
}
