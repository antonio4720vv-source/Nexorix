package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importar extractos en dos pasos:
 *
 *  1. preview(): lee el archivo, normaliza, clasifica y marca duplicados.
 *     Todavia NO crea movimientos.
 *  2. confirm(): la persona eligio que filas importar; ahi se crean.
 *
 * Asi nada entra a sus cuentas sin que lo haya revisado.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final Duration PREVIEW_LIFETIME = Duration.ofHours(24);

    /** Hora que se asigna a los movimientos importados (el extracto solo trae el dia). */
    private static final LocalTime IMPORTED_TIME = LocalTime.NOON;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("es", "CO"));

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CsvStatementParser csvParser;
    private final PdfStatementParser pdfParser;

    /**
     * @Autowired le dice a Spring cual constructor usar: hay dos
     * (el otro es solo para las pruebas).
     */
    @Autowired
    public ImportService(
            ImportBatchRepository batchRepository,
            ImportRowRepository rowRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService
    ) {
        this(batchRepository, rowRepository, accountRepository, userRepository,
                transactionRepository, transactionService,
                new CsvStatementParser(), new PdfStatementParser());
    }

    /** Constructor para pruebas. */
    ImportService(
            ImportBatchRepository batchRepository,
            ImportRowRepository rowRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            CsvStatementParser csvParser,
            PdfStatementParser pdfParser
    ) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.csvParser = csvParser;
        this.pdfParser = pdfParser;
    }

    // ============================================================
    // 1. VISTA PREVIA
    // ============================================================

    @Transactional
    public ImportPreview preview(
            String username,
            Long accountId,
            String fileName,
            byte[] bytes,
            String password
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Account account = ownedAccount(accountId, user);

        if (bytes == null || bytes.length == 0) {
            throw new ImportException(ImportException.INVALID_FILE, "El archivo está vacío.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ImportException(ImportException.INVALID_FILE, "El archivo pesa más de 5 MB.");
        }

        String cleanName = cleanFileName(fileName);
        String format = detectFormat(bytes, cleanName);
        String hash = sha256(bytes);

        batchRepository.findFirstByAccountIdAndFileHashAndStatus(accountId, hash, ImportBatch.CONFIRMED)
                .ifPresent(previous -> {
                    throw new ImportException(ImportException.ALREADY_IMPORTED,
                            "Ya importaste este mismo archivo en esta cuenta el "
                                    + previous.getConfirmedAt().toLocalDate().format(DAY) + ".");
                });

        List<ParsedMovement> parsed = "PDF".equals(format)
                ? pdfParser.parse(bytes, password)
                : csvParser.parse(bytes);

        if (parsed.isEmpty()) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "No encontramos movimientos en el archivo.");
        }

        ImportBatch batch = batchRepository.save(new ImportBatch(user, account, cleanName, hash, format));
        List<ImportRow> rows = buildRows(batch, account, parsed);

        batch.setTotalRows(rows.size());
        batchRepository.save(batch);
        rowRepository.saveAll(rows);

        log.info("Vista previa de importacion {} | {} | {} filas", batch.getId(), format, rows.size());

        return toPreview(batch, rows);
    }

    /**
     * Normaliza, clasifica y detecta duplicados.
     *
     * Duplicado = ya existe en esa cuenta un movimiento del MISMO dia,
     * MISMO tipo y MISMO valor. Cada movimiento existente solo puede
     * "tapar" una fila del archivo.
     *
     * Dos filas iguales dentro del mismo archivo NO se descartan (pueden
     * ser dos cafes iguales el mismo dia), solo se avisa.
     */
    public List<ImportRow> buildRows(ImportBatch batch, Account account, List<ParsedMovement> parsed) {

        LocalDate min = null;
        LocalDate max = null;
        for (ParsedMovement movement : parsed) {
            if (movement.isValid()) {
                min = min == null || movement.date().isBefore(min) ? movement.date() : min;
                max = max == null || movement.date().isAfter(max) ? movement.date() : max;
            }
        }

        List<Transaction> available = min == null ? new ArrayList<>()
                : new ArrayList<>(transactionRepository.findByAccountIdAndTransactionDateBetween(
                account.getId(), min.atStartOfDay(), max.plusDays(1).atStartOfDay()));

        Map<String, Integer> seenInFile = new HashMap<>();
        List<ImportRow> rows = new ArrayList<>();

        for (ParsedMovement movement : parsed) {

            if (!movement.isValid()) {
                rows.add(new ImportRow(batch, movement, null, ImportRow.INVALID, movement.error()));
                continue;
            }

            String category = TransactionClassifier.classify(movement.description(), movement.type());
            Transaction existing = takeMatch(available, movement);

            if (existing != null) {
                rows.add(new ImportRow(batch, movement, category, ImportRow.DUPLICATE,
                        "Ya está en Nexorix: \"" + existing.getDescription() + "\" del mismo día y valor."));
                continue;
            }

            String key = movement.date() + "|" + movement.type() + "|"
                    + movement.amount().stripTrailingZeros().toPlainString() + "|"
                    + StatementValues.normalizeForMatching(movement.description());

            Integer firstLine = seenInFile.putIfAbsent(key, movement.line());
            String message = firstLine != null
                    ? "Igual a la línea " + firstLine + " del archivo. Si no es un cobro repetido, desmárcala."
                    : movement.warning();

            rows.add(new ImportRow(batch, movement, category, ImportRow.NEW, message));
        }

        return rows;
    }

    private static Transaction takeMatch(Collection<Transaction> available, ParsedMovement movement) {
        Iterator<Transaction> iterator = available.iterator();
        while (iterator.hasNext()) {
            Transaction transaction = iterator.next();
            if (transaction.getTransactionDate().toLocalDate().equals(movement.date())
                    && transaction.getType().equalsIgnoreCase(movement.type())
                    && transaction.getAmount().compareTo(movement.amount()) == 0) {
                iterator.remove();
                return transaction;
            }
        }
        return null;
    }

    // ============================================================
    // 2. CONFIRMAR
    // ============================================================

    @Transactional
    public int confirm(String username, Long batchId, List<Long> selectedRowIds) {

        ImportBatch batch = ownedPreview(username, batchId);
        Set<Long> selected = selectedRowIds == null ? Set.of() : new HashSet<>(selectedRowIds);

        if (selected.isEmpty()) {
            throw new ImportException(ImportException.INVALID_FILE, "Elige al menos un movimiento para importar.");
        }

        int imported = 0;

        for (ImportRow row : rowRepository.findByBatchIdOrderByLineNumberAsc(batchId)) {

            if (!selected.contains(row.getId()) || ImportRow.INVALID.equals(row.getStatus())) {
                continue;
            }

            Transaction transaction = transactionService.saveImported(
                    batch.getAccount(),
                    row.getAmount(),
                    row.getType(),
                    row.getDescription(),
                    row.getMovementDate().atTime(IMPORTED_TIME),
                    row.getReference(),
                    row.getCategory()
            );

            row.setTransactionId(transaction.getId());
            imported++;
        }

        batch.confirm(imported);
        batchRepository.save(batch);

        log.info("Importacion {} confirmada: {} movimientos", batchId, imported);
        return imported;
    }

    @Transactional
    public void cancel(String username, Long batchId) {
        ImportBatch batch = ownedPreview(username, batchId);
        batch.cancel();
        batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public ImportPreview get(String username, Long batchId) {
        ImportBatch batch = owned(username, batchId);
        return toPreview(batch, rowRepository.findByBatchIdOrderByLineNumberAsc(batchId));
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    ImportPreview toPreview(ImportBatch batch, List<ImportRow> rows) {

        int news = 0, duplicates = 0, invalid = 0;
        // Mientras el archivo se procesa, todavia no hay filas: se devuelve el estado.
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<ImportPreview.Row> items = new ArrayList<>();

        for (ImportRow row : rows) {
            switch (row.getStatus()) {
                case ImportRow.NEW -> {
                    news++;
                    if ("INGRESO".equals(row.getType())) {
                        income = income.add(row.getAmount());
                    } else {
                        expense = expense.add(row.getAmount());
                    }
                }
                case ImportRow.DUPLICATE -> duplicates++;
                default -> invalid++;
            }

            items.add(new ImportPreview.Row(
                    row.getId(), row.getLineNumber(), row.getMovementDate(), row.getDescription(),
                    row.getAmount(), row.getType(), row.getReference(), row.getCategory(),
                    TransactionClassifier.label(row.getCategory()), row.getStatus(), row.getMessage(),
                    ImportRow.NEW.equals(row.getStatus())
            ));
        }

        return new ImportPreview(
                batch.getId(), batch.getFileName(), batch.getFormat(),
                batch.getAccount().getId(), batch.getAccount().getName(), batch.getStatus(),
                rows.size(), news, duplicates, invalid, income, expense, items,
                batch.isAiUsed(), batch.getCheckMessage(), batch.getErrorMessage()
        );
    }

    private Account ownedAccount(Long accountId, User user) {
        if (accountId == null) {
            throw new IllegalArgumentException("Elige la cuenta a la que pertenece el extracto.");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso sobre esta cuenta");
        }
        return account;
    }

    private ImportBatch owned(String username, Long batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || !batch.getUser().getUsername().equals(username)) {
            throw new ImportException(ImportException.NOT_FOUND, "No encontramos esa importación.");
        }
        return batch;
    }

    private ImportBatch ownedPreview(String username, Long batchId) {
        ImportBatch batch = owned(username, batchId);
        if (!ImportBatch.PREVIEW.equals(batch.getStatus())) {
            throw new ImportException(ImportException.NOT_FOUND, "Esta importación ya se cerró.");
        }
        if (batch.getCreatedAt().plus(PREVIEW_LIFETIME).isBefore(LocalDateTime.now())) {
            throw new ImportException(ImportException.NOT_FOUND,
                    "La vista previa venció. Sube el archivo de nuevo.");
        }
        return batch;
    }

    static String detectFormat(byte[] bytes, String fileName) {
        if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "PDF";
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return "CSV";
        }
        if (name.endsWith(".pdf")) {
            throw new ImportException(ImportException.INVALID_FILE, "El archivo no es un PDF válido.");
        }
        throw new ImportException(ImportException.INVALID_FILE,
                "Solo podemos leer extractos en PDF o CSV. Si tienes un Excel, guárdalo como CSV.");
    }

    static String cleanFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "extracto";
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return name.length() > 150 ? name.substring(name.length() - 150) : name;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular la huella del archivo.", exception);
        }
    }
}
