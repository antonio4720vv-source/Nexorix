package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Recibe VARIOS archivos a la vez y los pone en una cola.
 *
 * Disenado para muchas personas subiendo al mismo tiempo:
 *  - La peticion responde de inmediato; la lectura ocurre en segundo plano
 *    con un numero fijo de trabajadores (no se crean hilos sin control).
 *  - Los archivos esperan en disco, no en memoria.
 *  - Limites por persona: archivos por envio y archivos en proceso.
 *  - Si la cola esta llena, se responde "intenta en un minuto" en vez de caerse.
 */
@Service
public class ImportQueueService {

    private static final Logger log = LoggerFactory.getLogger(ImportQueueService.class);

    public static final List<String> ACTIVE = List.of(ImportBatch.QUEUED, ImportBatch.PROCESSING);

    public record Incoming(String fileName, byte[] bytes) {
    }

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ImportProcessor processor;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;
    private final int maxFilesPerUpload;
    private final int maxActivePerUser;
    private final Path tempDir;

    public ImportQueueService(
            ImportBatchRepository batchRepository,
            ImportRowRepository rowRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            ImportProcessor processor,
            @Qualifier("importExecutor") TaskExecutor executor,
            TransactionTemplate tx,
            @Value("${nexorix.import.max-files-per-upload:10}") int maxFilesPerUpload,
            @Value("${nexorix.import.max-active-per-user:20}") int maxActivePerUser
    ) throws IOException {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.processor = processor;
        this.executor = executor;
        this.tx = tx;
        this.maxFilesPerUpload = maxFilesPerUpload;
        this.maxActivePerUser = maxActivePerUser;
        this.tempDir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "nexorix-imports"));
    }

    // ============================================================
    // RECIBIR ARCHIVOS
    // ============================================================

    public List<ImportStatus> enqueue(String username, Long accountId, List<Incoming> files, String password) {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un archivo.");
        }
        if (files.size() > maxFilesPerUpload) {
            throw new IllegalArgumentException("Puedes subir máximo " + maxFilesPerUpload + " archivos a la vez.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Account account = accountRepository.findById(accountId == null ? -1L : accountId)
                .orElseThrow(() -> new IllegalArgumentException("Elige la cuenta del extracto."));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso sobre esta cuenta");
        }

        long active = batchRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE);
        if (active + files.size() > maxActivePerUser) {
            throw new ImportException(ImportException.INVALID_FILE,
                    "Tienes " + active + " archivos en proceso. Espera a que terminen para subir más.");
        }

        List<ImportStatus> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Incoming file : files) {
            String name = ImportService.cleanFileName(file.fileName());
            try {
                results.add(enqueueOne(user, account, name, file.bytes(), password, seen));
            } catch (ImportException exception) {
                results.add(ImportStatus.rejected(name, exception.getMessage()));
            }
        }
        return results;
    }

    private ImportStatus enqueueOne(User user, Account account, String name, byte[] bytes,
                                    String password, Set<String> seen) {

        if (bytes == null || bytes.length == 0) {
            throw new ImportException(ImportException.INVALID_FILE, "El archivo está vacío.");
        }
        if (bytes.length > ImportService.MAX_BYTES) {
            throw new ImportException(ImportException.INVALID_FILE, "El archivo pesa más de 5 MB.");
        }

        String format = ImportService.detectFormat(bytes, name);
        String hash = ImportService.sha256(bytes);

        if (!seen.add(hash)) {
            throw new ImportException(ImportException.ALREADY_IMPORTED, "Este archivo está repetido en tu selección.");
        }
        if (batchRepository.findFirstByAccountIdAndFileHashAndStatus(
                account.getId(), hash, ImportBatch.CONFIRMED).isPresent()) {
            throw new ImportException(ImportException.ALREADY_IMPORTED,
                    "Ya importaste este mismo archivo en esta cuenta.");
        }

        Path path;
        try {
            path = Files.createTempFile(tempDir, "extracto-", "." + format.toLowerCase());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new ImportException(ImportException.INVALID_FILE, "No pudimos recibir el archivo. Intenta de nuevo.");
        }

        ImportBatch batch = tx.execute(s -> {
            ImportBatch created = new ImportBatch(user, account, name, hash, format);
            created.markQueued();
            return batchRepository.save(created);
        });

        Long id = batch.getId();
        try {
            executor.execute(() -> processor.process(id, path, format, password));
        } catch (TaskRejectedException exception) {
            log.warn("Cola de importacion llena; archivo {} rechazado", id);
            deleteQuietly(path);
            tx.executeWithoutResult(s -> batchRepository.findById(id).ifPresent(b -> {
                b.fail("Hay muchos archivos en cola en este momento. Intenta en un minuto.");
                batchRepository.save(b);
            }));
            return ImportStatus.rejected(name, "Hay muchos archivos en cola en este momento. Intenta en un minuto.");
        }

        return ImportStatus.of(batch);
    }

    // ============================================================
    // CONSULTAR ESTADO
    // ============================================================

    public List<ImportStatus> status(String username, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> limited = ids.stream().limit(50).toList();
        return batchRepository.findByIdInAndUserUsername(limited, username).stream()
                .map(ImportStatus::of)
                .toList();
    }

    // ============================================================
    // MANTENIMIENTO
    // ============================================================

    /** Si el servidor se reinicio con archivos a medias, se marcan para volver a subirlos. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        tx.executeWithoutResult(s -> {
            List<ImportBatch> stuck = batchRepository.findByStatusIn(ACTIVE);
            stuck.forEach(b -> b.fail("El servidor se reinició mientras se leía este archivo. Súbelo de nuevo."));
            batchRepository.saveAll(stuck);
            if (!stuck.isEmpty()) {
                log.info("{} archivos interrumpidos marcados para volver a subir", stuck.size());
            }
        });
    }

    /** Cada hora: borra vistas previas viejas (24 h) y archivos temporales olvidados. */
    @Scheduled(cron = "0 15 * * * *")
    public void cleanup() {
        LocalDateTime limit = LocalDateTime.now().minusHours(24);
        tx.executeWithoutResult(s -> {
            List<ImportBatch> old = batchRepository.findByStatusInAndCreatedAtBefore(
                    List.of(ImportBatch.PREVIEW, ImportBatch.FAILED, ImportBatch.CANCELLED), limit);
            for (ImportBatch batch : old) {
                rowRepository.deleteByBatchId(batch.getId());
            }
            batchRepository.deleteAll(old);
            if (!old.isEmpty()) {
                log.info("Limpieza: {} importaciones viejas borradas", old.size());
            }
        });

        Instant fileLimit = Instant.now().minus(1, ChronoUnit.DAYS);
        try (Stream<Path> files = Files.list(tempDir)) {
            files.filter(p -> {
                try {
                    return Files.getLastModifiedTime(p).toInstant().isBefore(fileLimit);
                } catch (IOException e) {
                    return false;
                }
            }).forEach(ImportQueueService::deleteQuietly);
        } catch (IOException ignored) {
            // nada que limpiar
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // se intentara en la proxima limpieza
        }
    }
}
