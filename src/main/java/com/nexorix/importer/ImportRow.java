package com.nexorix.importer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila leida del extracto, lista para revisar antes de importarla. */
@Entity
@Table(name = "import_rows")
public class ImportRow {

    /** Se puede importar. */
    public static final String NEW = "NEW";
    /** Parece que ya existe en Nexorix: por defecto NO se importa. */
    public static final String DUPLICATE = "DUPLICATE";
    /** No se pudo leer bien: no se puede importar. */
    public static final String INVALID = "INVALID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "movement_date")
    private LocalDate movementDate;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String type;

    @Column(length = 150)
    private String reference;

    @Column(length = 40)
    private String category;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 255)
    private String message;

    /** Id del movimiento creado al confirmar. */
    @Column(name = "transaction_id")
    private Long transactionId;

    protected ImportRow() {
    }

    public ImportRow(ImportBatch batch, ParsedMovement parsed, String category,
                     String status, String message) {
        this.batch = batch;
        this.lineNumber = parsed.line();
        this.movementDate = parsed.date();
        this.description = parsed.description();
        this.amount = parsed.amount();
        this.type = parsed.type();
        this.reference = parsed.reference();
        this.category = category;
        this.status = status;
        this.message = message == null || message.length() <= 255 ? message : message.substring(0, 255);
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getId() { return id; }
    public ImportBatch getBatch() { return batch; }
    public int getLineNumber() { return lineNumber; }
    public LocalDate getMovementDate() { return movementDate; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public String getReference() { return reference; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Long getTransactionId() { return transactionId; }
}
