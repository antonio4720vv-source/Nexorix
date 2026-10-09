package com.nexorix.security;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** IP bloqueada hasta cierta hora. Se guarda para que el bloqueo sobreviva a un reinicio. */
@Entity
@Table(name = "banned_ips")
public class BannedIp {

    @Id
    @Column(length = 45)
    private String ip;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "banned_until", nullable = false)
    private LocalDateTime bannedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Ya se reporto a AbuseIPDB. */
    @Column(nullable = false)
    private boolean reported;

    protected BannedIp() {
    }

    public BannedIp(String ip, String reason, LocalDateTime bannedUntil) {
        this.ip = ip;
        this.reason = reason;
        this.bannedUntil = bannedUntil;
        this.createdAt = LocalDateTime.now();
    }

    public String getIp() { return ip; }
    public String getReason() { return reason; }
    public LocalDateTime getBannedUntil() { return bannedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isReported() { return reported; }
    public void setReported(boolean reported) { this.reported = reported; }
}
