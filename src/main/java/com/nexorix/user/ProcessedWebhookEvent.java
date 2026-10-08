package com.nexorix.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Registro de cada webhook ya procesado.
 * Didit usa el mismo event_id cuando reintenta un envio, asi que si
 * el event_id ya esta aqui, el webhook es repetido y se ignora.
 */
@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "webhook_type", length = 100)
    private String webhookType;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(length = 50)
    private String status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected ProcessedWebhookEvent() {
    }

    public ProcessedWebhookEvent(
            String eventId,
            String provider,
            String webhookType,
            String sessionId,
            String status
    ) {
        this.eventId = eventId;
        this.provider = provider;
        this.webhookType = webhookType;
        this.sessionId = sessionId;
        this.status = status;
        this.receivedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getProvider() {
        return provider;
    }

    public String getWebhookType() {
        return webhookType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
