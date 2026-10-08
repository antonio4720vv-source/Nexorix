package com.nexorix.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Escribe y lee el historial de auditoria del Contador.
 *
 * Cada anotacion va en su PROPIA transaccion: queda guardada aunque el paso
 * que se estaba auditando falle y se deshaga (por ejemplo un archivo que no se
 * pudo leer). Y si la auditoria misma falla, nunca tumba la importacion.
 */
@Component
public class ImportAudit {

    private static final Logger log = LoggerFactory.getLogger(ImportAudit.class);

    public static final String RECEIVED = "RECIBIDO";
    public static final String REJECTED = "RECHAZADO";
    public static final String QUEUED = "EN_COLA";
    public static final String PROCESSING = "PROCESANDO";
    public static final String READ = "LEIDO";
    public static final String AI_CATEGORIES = "CATEGORIAS_IA";
    public static final String FAILED = "ERROR";
    public static final String CONFIRMED = "CONFIRMADO";
    public static final String CANCELLED = "CANCELADO";
    public static final String EXPIRED = "DESCARTADO";

    public static final int MAX_LIST = 500;

    private final ImportAuditRepository repository;

    /**
     * Transaccion propia por anotacion. Se usa una plantilla y no @Transactional porque
     * recordAfterCommit() llama a record() dentro de esta misma clase, y asi la anotacion
     * no se aplicaria (los eventos se perderian sin avisar).
     */
    private final TransactionTemplate ownTransaction;

    public ImportAudit(ImportAuditRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long userId, Long accountId, Long batchId, String fileName, String fileHash,
                       String event, String detail) {
        save(userId, accountId, batchId, fileName, fileHash, event, detail);
    }

    /** Anota un evento de un lote ya creado. */
    public void record(ImportBatch batch, String event, String detail) {
        save(batch.getUser().getId(), batch.getAccount().getId(), batch.getId(),
                batch.getFileName(), batch.getFileHash(), event, detail);
    }

    /**
     * Anota el evento SOLO si la transaccion en curso se confirma (si se deshace, no paso).
     * Fuera de una transaccion, lo anota de inmediato.
     */
    public void recordAfterCommit(ImportBatch batch, String event, String detail) {
        Long userId = batch.getUser().getId();
        Long accountId = batch.getAccount().getId();
        Long batchId = batch.getId();
        String fileName = batch.getFileName();
        String fileHash = batch.getFileHash();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record(userId, accountId, batchId, fileName, fileHash, event, detail);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                record(userId, accountId, batchId, fileName, fileHash, event, detail);
            }
        });
    }

    private void save(Long userId, Long accountId, Long batchId, String fileName, String fileHash,
                      String event, String detail) {
        try {
            ownTransaction.executeWithoutResult(status ->
                    repository.save(new ImportAuditEvent(userId, accountId, batchId, fileName, fileHash, event, detail)));
        } catch (RuntimeException exception) {
            log.error("No se pudo guardar la auditoria ({} del archivo {})", event, batchId, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<ImportAuditEvent> recent(Long userId, int limit) {
        return repository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIST))));
    }

    @Transactional(readOnly = true)
    public List<ImportAuditEvent> ofBatch(Long userId, Long batchId) {
        return repository.findByUserIdAndBatchIdOrderByIdAsc(userId, batchId);
    }
}
