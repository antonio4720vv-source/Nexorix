package com.nexorix.split;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una cuenta compartida: una persona (el pagador) pago el total y los demas
 * le reembolsan su parte. Nexorix no mueve dinero: solo lleva la cuenta de quien debe que.
 */
@Entity
@Table(name = "split_expenses", indexes = @Index(name = "idx_split_expenses_payer", columnList = "payer_id"))
public class SplitExpense {

    public static final String OPEN = "ABIERTA";
    public static final String CANCELLED = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** Lo que le toca al pagador (puede ser unos centavos mas por el redondeo). */
    @Column(name = "payer_share", nullable = false, precision = 19, scale = 2)
    private BigDecimal payerShare;

    @Column(nullable = false, length = 10)
    private String status = OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SplitExpense() {
    }

    public SplitExpense(User payer, String title, BigDecimal totalAmount, BigDecimal payerShare) {
        this.payer = payer;
        this.title = title;
        this.totalAmount = totalAmount;
        this.payerShare = payerShare;
        this.createdAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = CANCELLED;
    }

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }

    public Long getId() { return id; }
    public User getPayer() { return payer; }
    public String getTitle() { return title; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPayerShare() { return payerShare; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
