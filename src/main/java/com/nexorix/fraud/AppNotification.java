package com.nexorix.fraud;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aviso dentro de la app (la campanita). Es el unico canal de las anomalias leves;
 * las alertas criticas ademas salen por WhatsApp y SMS, y aqui queda anotado cuales llegaron.
 */
@Entity
@Table(name = "app_notifications", indexes = @Index(name = "idx_notif_user_time", columnList = "user_id, created_at"))
public class AppNotification {

    public enum Severity { INFO, WARNING, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** Canales por los que salio, ej. "APP,WHATSAPP,SMS". */
    @Column(nullable = false, length = 80)
    private String channels = "APP";

    @Column(name = "bank_event_id")
    private Long bankEventId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AppNotification() {
    }

    public AppNotification(User user, Severity severity, String title, String body, Long bankEventId) {
        this.user = user;
        this.severity = severity;
        this.title = title.length() > 120 ? title.substring(0, 120) : title;
        this.body = body.length() > 500 ? body.substring(0, 500) : body;
        this.bankEventId = bankEventId;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public Long getBankEventId() {
        return bankEventId;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
