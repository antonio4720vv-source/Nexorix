package com.nexorix.importer;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Una linea del historial de auditoria del Contador: que le paso a un archivo,
 * cuando y con que resultado.
 *
 * Es solo de ESCRITURA (nunca se edita) y no depende del lote: la limpieza
 * borra los lotes viejos, pero el historial queda. Por eso guarda su propia
 * copia del nombre del archivo y de su huella SHA-256.
 *
 * Nunca guarda el contenido del archivo, la contrasena del PDF ni descripciones
 * de los movimientos: solo cantidades, fechas, metodo y resultado.
 */
@Entity
@Table(name = "import_audit_log", indexes = {
        @Index(name = "idx_import_audit_user_time", columnList = "user_id,created_at"),
        @Index(name = "idx_import_audit_batch", columnList = "batch_id")
})
public class ImportAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id")
    private Long accountId;

    /** Lote del archivo; vacio si el archivo se rechazo antes de crearlo. */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "file_name", length = 150)
    private String fileName;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(nullable = false, length = 30)
    private String event;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ImportAuditEvent() {
    }

    public ImportAuditEvent(Long userId, Long accountId, Long batchId, String fileName, String fileHash,
                            String event, String detail) {
        this.userId = userId;
        this.accountId = accountId;
        this.batchId = batchId;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.event = event;
        this.detail = detail == null ? null : detail.length() > 500 ? detail.substring(0, 497) + "..." : detail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public Long getBatchId() { return batchId; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public String getEvent() { return event; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
