package com.nexorix.trace;

import com.nexorix.transaction.Transaction;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila de conciliacion: un movimiento de salida relacionado con uno de entrada.
 *
 * Trace V2: una transferencia 1 -> N o N -> 1 se guarda como VARIAS filas que
 * comparten el mismo groupId. Una transferencia 1 -> 1 es un grupo de una fila.
 *
 * matchedAmount: dinero que paso entre cuentas propias en esta fila.
 * feeAmount: diferencia atribuida a comisiones (solo en una fila del grupo).
 */
@Entity
@Table(name = "reconciliation_matches", indexes = {
        @Index(name = "idx_recon_group", columnList = "group_id"),
        @Index(name = "idx_recon_origin", columnList = "origin_transaction_id"),
        @Index(name = "idx_recon_destination", columnList = "destination_transaction_id")
})
public class ReconciliationMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_transaction_id", nullable = false)
    private Transaction originTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_transaction_id", nullable = false)
    private Transaction destinationTransaction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal matchedAmount;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(nullable = false, length = 100)
    private String classification;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // ------------------------------------------------------------
    // Trace V2 (las filas antiguas quedan con estos campos vacios)
    // ------------------------------------------------------------

    /** Identificador del grupo (transferencia completa). Null en filas antiguas. */
    @Column(name = "group_id", length = 40)
    private String groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20)
    private TraceKind kind;

    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount;

    /** Cuando la persona deshizo la conciliacion (estado UNMATCHED). */
    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    protected ReconciliationMatch() {
    }

    /** Constructor original (1 -> 1). */
    public ReconciliationMatch(
            Transaction originTransaction,
            Transaction destinationTransaction,
            BigDecimal matchedAmount,
            int score,
            ReconciliationStatus status,
            String classification
    ) {
        this(originTransaction, destinationTransaction, matchedAmount, BigDecimal.ZERO, score, status,
                classification, null, TraceKind.ONE_TO_ONE);
    }

    public ReconciliationMatch(
            Transaction originTransaction,
            Transaction destinationTransaction,
            BigDecimal matchedAmount,
            BigDecimal feeAmount,
            int score,
            ReconciliationStatus status,
            String classification,
            String groupId,
            TraceKind kind
    ) {
        this.originTransaction = originTransaction;
        this.destinationTransaction = destinationTransaction;
        this.matchedAmount = matchedAmount;
        this.feeAmount = feeAmount == null ? BigDecimal.ZERO : feeAmount;
        this.score = score;
        this.status = status;
        this.classification = classification;
        this.groupId = groupId;
        this.kind = kind;
        this.createdAt = LocalDateTime.now();
    }

    /** Deshacer: la fila queda en el historial, pero ya no cuenta como transferencia interna. */
    public void undo() {
        this.status = ReconciliationStatus.UNMATCHED;
        this.undoneAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == ReconciliationStatus.MATCHED;
    }

    /** Grupo efectivo: las filas antiguas son un grupo por si solas. */
    public String effectiveGroupId() {
        return groupId != null ? groupId : "m" + id;
    }

    public TraceKind effectiveKind() {
        return kind != null ? kind : TraceKind.ONE_TO_ONE;
    }

    public BigDecimal effectiveFee() {
        return feeAmount != null ? feeAmount : BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public Transaction getOriginTransaction() {
        return originTransaction;
    }

    public Transaction getDestinationTransaction() {
        return destinationTransaction;
    }

    public BigDecimal getMatchedAmount() {
        return matchedAmount;
    }

    public int getScore() {
        return score;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public String getClassification() {
        return classification;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getGroupId() {
        return groupId;
    }

    public TraceKind getKind() {
        return kind;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public LocalDateTime getUndoneAt() {
        return undoneAt;
    }
}
