package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un webhook recibido. event_id es unico: si el banco reintenta la misma notificacion no se duplica.
 * Un movimiento BLOQUEADO todavia no existe como transaccion ni toca el saldo.
 */
@Entity
@Table(name = "bank_events", indexes = @Index(name = "idx_bank_events_user", columnList = "user_id, occurred_at"))
public class BankEvent {

    public enum Status { APPLIED, BLOCKED, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 40)
    private String bank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankMovementKind kind;

    /** DEBIT (sale plata) o CREDIT (entra). */
    @Column(nullable = false, length = 6)
    private String direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Comercio, o nombre del destinatario / remitente. */
    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "counterparty_ref", length = 100)
    private String counterpartyRef;

    @Column(length = 80)
    private String city;

    @Column(length = 2)
    private String country;

    private Double latitude;

    private Double longitude;

    @Column(length = 45)
    private String ip;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** NONE, ANOMALY o CRITICAL. */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel = "NONE";

    @Column(name = "risk_reason", length = 300)
    private String riskReason;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected BankEvent() {
    }

    public BankEvent(String eventId, User user, Account account, String bank, BankMovementKind kind,
                     String direction, BigDecimal amount, String label, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.user = user;
        this.account = account;
        this.bank = bank;
        this.kind = kind;
        this.direction = direction;
        this.amount = amount;
        this.label = label.length() > 150 ? label.substring(0, 150) : label;
        this.occurredAt = occurredAt;
    }

    public void place(String city, String country, Double latitude, Double longitude, String ip) {
        this.city = city;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ip = ip;
    }

    public void counterpartyRef(String ref) {
        this.counterpartyRef = ref;
    }

    public void risk(String level, String reason) {
        this.riskLevel = level;
        this.riskReason = reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason;
    }

    public void applied(Status status, Long transactionId) {
        this.status = status;
        this.transactionId = transactionId;
    }

    public void blocked() {
        this.status = Status.BLOCKED;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public String getBank() {
        return bank;
    }

    public BankMovementKind getKind() {
        return kind;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getLabel() {
        return label;
    }

    public String getCounterpartyRef() {
        return counterpartyRef;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getIp() {
        return ip;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getRiskReason() {
        return riskReason;
    }

    public Long getTransactionId() {
        return transactionId;
    }
}
