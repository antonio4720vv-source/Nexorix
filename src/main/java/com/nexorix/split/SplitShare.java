package com.nexorix.split;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que una persona le debe al pagador de una cuenta compartida.
 *
 * Estados: PENDIENTE -> REPORTADO (la persona dice "ya pague") -> SALDADO
 * (el pagador confirma que lo recibio). El pagador tambien puede saldar
 * directamente (por ejemplo si le pagaron en efectivo).
 */
@Entity
@Table(name = "split_shares", indexes = {
        @Index(name = "idx_split_shares_participant", columnList = "participant_id"),
        @Index(name = "idx_split_shares_expense", columnList = "expense_id")
})
public class SplitShare {

    public static final String PENDING = "PENDIENTE";
    public static final String REPORTED = "REPORTADO";
    public static final String SETTLED = "SALDADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private SplitExpense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private User participant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    /** Va en el enlace de cobro. Largo y aleatorio; ademas el enlace exige iniciar sesion. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    protected SplitShare() {
    }

    public SplitShare(SplitExpense expense, User participant, BigDecimal amount, String token) {
        this.expense = expense;
        this.participant = participant;
        this.amount = amount;
        this.token = token;
    }

    /** La persona avisa que ya pago. No cambia nada si ya esta saldado. */
    public void reportPaid() {
        if (SETTLED.equals(status)) {
            return;
        }
        this.status = REPORTED;
        this.reportedAt = LocalDateTime.now();
    }

    public void settle() {
        this.status = SETTLED;
        this.settledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public SplitExpense getExpense() { return expense; }
    public User getParticipant() { return participant; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getToken() { return token; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
}
