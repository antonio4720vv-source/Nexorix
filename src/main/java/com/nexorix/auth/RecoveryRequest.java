package com.nexorix.auth;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Una solicitud para recuperar la contrasena o el PIN.
 *
 * Pasos (status):
 *   CODE_SENT          -> se envio un codigo al correo
 *   CODE_VERIFIED      -> la persona escribio el codigo correcto
 *   IDENTITY_PENDING   -> esta verificando su identidad en Didit
 *   IDENTITY_APPROVED  -> Didit confirmo que es la duena de la cuenta
 *   IDENTITY_DECLINED  -> Didit no lo confirmo (puede reintentar)
 *   COMPLETED          -> ya cambio su contrasena y/o PIN
 *   CANCELLED          -> se cancelo o se reemplazo por otra solicitud
 */
@Entity
@Table(name = "recovery_requests")
public class RecoveryRequest {

    public static final String CODE_SENT = "CODE_SENT";
    public static final String CODE_VERIFIED = "CODE_VERIFIED";
    public static final String IDENTITY_PENDING = "IDENTITY_PENDING";
    public static final String IDENTITY_APPROVED = "IDENTITY_APPROVED";
    public static final String IDENTITY_DECLINED = "IDENTITY_DECLINED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Huella (BCrypt) del codigo de 6 digitos. El codigo nunca se guarda. */
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "code_attempts", nullable = false)
    private int codeAttempts = 0;

    @Column(nullable = false, length = 30)
    private String status;

    /** session_id de Didit de la verificacion de identidad. */
    @Column(name = "didit_session_id", unique = true, length = 100)
    private String diditSessionId;

    @Column(name = "verification_url", length = 1000)
    private String verificationUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected RecoveryRequest() {
    }

    public RecoveryRequest(User user, String codeHash, LocalDateTime expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.status = CODE_SENT;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isOpen() {
        return !COMPLETED.equals(status) && !CANCELLED.equals(status);
    }

    public int registerCodeAttempt() {
        return ++codeAttempts;
    }

    public void codeVerified(LocalDateTime newExpiry) {
        this.status = CODE_VERIFIED;
        this.expiresAt = newExpiry;
    }

    public void identityStarted(String sessionId, String url) {
        this.status = IDENTITY_PENDING;
        this.diditSessionId = sessionId;
        this.verificationUrl = url;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void complete() {
        this.status = COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getCodeAttempts() {
        return codeAttempts;
    }

    public String getStatus() {
        return status;
    }

    public String getDiditSessionId() {
        return diditSessionId;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
