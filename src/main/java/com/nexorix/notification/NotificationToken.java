package com.nexorix.notification;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Llave que usa la app del celular para enviar notificaciones bancarias.
 * Solo se guarda su huella (SHA-256): la llave en si se muestra una unica vez.
 */
@Entity
@Table(name = "notification_tokens")
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private long received;

    protected NotificationToken() {
    }

    public NotificationToken(User user, String tokenHash) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = LocalDateTime.now();
    }

    public User getUser() {
        return user;
    }

    public void replaceWith(String newHash) {
        this.tokenHash = newHash;
        this.createdAt = LocalDateTime.now();
        this.lastUsedAt = null;
        this.received = 0;
    }

    public void markUsed(boolean registered) {
        this.lastUsedAt = LocalDateTime.now();
        if (registered) {
            this.received++;
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public long getReceived() {
        return received;
    }
}
