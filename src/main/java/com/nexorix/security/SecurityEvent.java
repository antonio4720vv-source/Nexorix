package com.nexorix.security;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Una linea del registro de seguridad: que hizo una IP sospechosa, cuando y con que.
 * Es solo de escritura. Nunca guarda cookies, tokens ni contrasenas.
 */
@Entity
@Table(name = "security_events", indexes = {
        @Index(name = "idx_security_events_ip_time", columnList = "ip,created_at"),
        @Index(name = "idx_security_events_time", columnList = "created_at")
})
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String ip;

    /** HONEYPOT_PATH, HONEYPOT_FIELD, PAYLOAD_SOSPECHOSO, FUERZA_BRUTA, INUNDACION, BAN... */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 10)
    private String method;

    @Column(length = 300)
    private String path;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(length = 2000)
    private String headers;

    @Column(length = 600)
    private String payload;

    @Column(length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SecurityEvent() {
    }

    public SecurityEvent(String ip, String type, String method, String path, String userAgent,
                         String headers, String payload, String detail) {
        this.ip = ip;
        this.type = type;
        this.method = method;
        this.path = path;
        this.userAgent = userAgent;
        this.headers = headers;
        this.payload = payload;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getIp() { return ip; }
    public String getType() { return type; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getUserAgent() { return userAgent; }
    public String getHeaders() { return headers; }
    public String getPayload() { return payload; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
