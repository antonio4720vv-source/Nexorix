package com.nexorix.notification;

import com.nexorix.security.EncryptedStringConverter;
import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Registro de cada aviso de banco que llega del celular, para que la persona vea que paso con
 * cada uno (registrado, repetido, bloqueado, no entendido...). El texto solo se guarda (cifrado)
 * cuando NO se entendio, para poder revisarlo; nunca el de avisos con claves o codigos.
 */
@Entity
@Table(name = "notification_logs", indexes = @Index(name = "idx_notification_logs_user", columnList = "user_id, received_at"))
public class NotificationLog {

    public static final String REGISTERED = "REGISTERED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String BLOCKED = "BLOCKED";
    public static final String UNRECOGNIZED = "UNRECOGNIZED";
    public static final String INFO = "INFO";
    public static final String SENSITIVE = "SENSITIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "app_name", length = 100)
    private String appName;

    @Column(length = 100)
    private String bank;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 300)
    private String summary;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1200)
    private String snippet;

    protected NotificationLog() {
    }

    public NotificationLog(User user, String appName, String bank, String result, String summary, String snippet) {
        this.user = user;
        this.appName = appName == null ? null : cut(appName, 100);
        this.bank = bank;
        this.result = result;
        this.summary = summary == null ? null : cut(summary, 300);
        this.snippet = snippet == null ? null : cut(snippet, 400);
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getAppName() {
        return appName;
    }

    public String getBank() {
        return bank;
    }

    public String getResult() {
        return result;
    }

    public String getSummary() {
        return summary;
    }

    public String getSnippet() {
        return snippet;
    }
}
