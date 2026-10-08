package com.nexorix.fraud;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historial de comportamiento de una persona (solo se agrega, nunca se edita).
 * Cada fila es una compra o una transferencia a un tercero YA aceptada. De aqui salen:
 * comercios y categorias habituales, destinatarios frecuentes y ubicaciones comunes.
 */
@Entity
@Table(name = "user_behavior_log", indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id, occurred_at"),
        @Index(name = "idx_behavior_user_merchant", columnList = "user_id, merchant_key"),
        @Index(name = "idx_behavior_user_recipient", columnList = "user_id, recipient_key")
})
public class UserBehaviorLog {

    public enum EventType { PURCHASE, TRANSFER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "merchant_key", length = 100)
    private String merchantKey;

    @Column(name = "merchant_name", length = 150)
    private String merchantName;

    @Column(length = 40)
    private String category;

    @Column(name = "recipient_key", length = 100)
    private String recipientKey;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 2)
    private String country;

    @Column(length = 80)
    private String city;

    private Double latitude;

    private Double longitude;

    @Column(length = 45)
    private String ip;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected UserBehaviorLog() {
    }

    public UserBehaviorLog(User user, EventType eventType, LocalDateTime occurredAt) {
        this.user = user;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public UserBehaviorLog merchant(String key, String name, String category) {
        this.merchantKey = key;
        this.merchantName = name;
        this.category = category;
        return this;
    }

    public UserBehaviorLog recipient(String key, String name) {
        this.recipientKey = key;
        this.recipientName = name;
        return this;
    }

    public UserBehaviorLog amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public UserBehaviorLog place(Geo geo, String ip) {
        if (geo != null) {
            this.country = geo.country();
            this.city = geo.city();
            this.latitude = geo.latitude();
            this.longitude = geo.longitude();
        }
        this.ip = ip;
        return this;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public String getCategory() {
        return category;
    }

    public String getRecipientKey() {
        return recipientKey;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    /** Lugar de la fila, o null si no tenia coordenadas. */
    public Geo geo() {
        return latitude == null || longitude == null ? null : new Geo(city, country, latitude, longitude);
    }
}
