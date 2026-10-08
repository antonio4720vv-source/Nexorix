package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Un archivo (extracto) que la persona subio.
 * No se guarda el archivo: solo su huella (SHA-256) para no importarlo dos veces.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    /** Recibido, esperando turno para leerse. */
    public static final String QUEUED = "QUEUED";
    /** Leyendose (reglas y, si hace falta, IA). */
    public static final String PROCESSING = "PROCESSING";
    /** No se pudo leer. */
    public static final String FAILED = "FAILED";
    public static final String PREVIEW = "PREVIEW";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false, length = 150)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** Evita que dos confirmaciones simultaneas importen el mismo archivo dos veces. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version = 0L;

    @Column(name = "new_rows", nullable = false, columnDefinition = "integer default 0")
    private int newRows;

    @Column(name = "duplicate_rows", nullable = false, columnDefinition = "integer default 0")
    private int duplicateRows;

    @Column(name = "invalid_rows", nullable = false, columnDefinition = "integer default 0")
    private int invalidRows;

    /** true si el archivo se leyo con IA. */
    @Column(name = "ai_used", nullable = false, columnDefinition = "boolean default false")
    private boolean aiUsed;

    /** Resultado de comparar con los totales del extracto ("cuadre"). */
    @Column(name = "check_message", length = 500)
    private String checkMessage;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected ImportBatch() {
    }

    public ImportBatch(User user, Account account, String fileName, String fileHash, String format) {
        this.user = user;
        this.account = account;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.format = format;
        this.status = PREVIEW;
        this.createdAt = LocalDateTime.now();
    }

    public void confirm(int imported) {
        this.status = CONFIRMED;
        this.importedRows = imported;
        this.confirmedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = CANCELLED;
    }

    public void markQueued() {
        this.status = QUEUED;
    }

    public void markProcessing() {
        this.status = PROCESSING;
    }

    public void markPreview(int total, int news, int duplicates, int invalid, boolean ai, String check) {
        this.status = PREVIEW;
        this.totalRows = total;
        this.newRows = news;
        this.duplicateRows = duplicates;
        this.invalidRows = invalid;
        this.aiUsed = ai;
        this.checkMessage = cut(check);
        this.errorMessage = null;
    }

    public void fail(String message) {
        this.status = FAILED;
        this.errorMessage = cut(message);
    }

    private static String cut(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Account getAccount() { return account; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public String getFormat() { return format; }
    public String getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getImportedRows() { return importedRows; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public int getNewRows() { return newRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public int getInvalidRows() { return invalidRows; }
    public boolean isAiUsed() { return aiUsed; }
    public String getCheckMessage() { return checkMessage; }
    public String getErrorMessage() { return errorMessage; }
}
