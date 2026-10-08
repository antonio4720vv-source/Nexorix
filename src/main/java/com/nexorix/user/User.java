package com.nexorix.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexorix.security.EncryptedStringConverter;
import com.nexorix.security.FieldCipher;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Cifrado en la base de datos (AES-256-GCM). Para buscar se usa emailIndex. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 400)
    private String email;

    /** Cifrado en la base de datos (AES-256-GCM). Para buscar se usa cedulaIndex. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 400)
    private String cedula;

    /** Huella (HMAC) del correo: permite buscarlo y exigir que sea unico sin guardarlo en claro. */
    @JsonIgnore
    @Column(name = "email_idx", unique = true, length = 64)
    private String emailIndex;

    /** Huella (HMAC) de la cedula. */
    @JsonIgnore
    @Column(name = "cedula_idx", unique = true, length = 64)
    private String cedulaIndex;

    /** Contrasena (BCrypt). Se pide en cada inicio de sesion. */
    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /**
     * PIN de 6 digitos (BCrypt). Se crea DESPUES de verificar la
     * identidad, por eso puede estar vacio al principio.
     */
    @JsonIgnore
    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 30)
    private KycStatus kycStatus;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // ------------------------------------------------------------
    // Proteccion contra intentos repetidos
    // ------------------------------------------------------------

    /** Contrasenas incorrectas seguidas. */
    @Column(name = "failed_password_attempts", nullable = false,
            columnDefinition = "integer default 0")
    private int failedPasswordAttempts = 0;

    /** PIN incorrectos seguidos. */
    @Column(name = "failed_pin_attempts", nullable = false,
            columnDefinition = "integer default 0")
    private int failedPinAttempts = 0;

    /** Si tiene valor y es futuro, la cuenta esta bloqueada hasta ese momento. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Opt-in: si es false, Nexorix no escribe por WhatsApp por gastos comunes (las alertas criticas si). */
    @Column(name = "whatsapp_notifications_enabled", nullable = false,
            columnDefinition = "boolean default false")
    private boolean whatsappNotificationsEnabled = false;

    /** Celular (solo digitos, con indicativo) para alertas criticas por WhatsApp y SMS. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "security_phone", length = 400)
    private String securityPhone;

    protected User() {
    }

    public User(
            String name,
            String username,
            String email,
            String cedula,
            String passwordHash
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.name = name;
        this.username = username;
        setEmail(email);
        setCedula(cedula);
        this.passwordHash = passwordHash;
        this.pinHash = null;
        this.kycStatus = KycStatus.PENDING;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        this.emailIndex = FieldCipher.blindIndex(email);
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
        this.cedulaIndex = FieldCipher.blindIndex(cedula);
    }

    public String getEmailIndex() {
        return emailIndex;
    }

    public String getCedulaIndex() {
        return cedulaIndex;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public boolean hasPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public boolean isWhatsappNotificationsEnabled() {
        return whatsappNotificationsEnabled;
    }

    public void setWhatsappNotificationsEnabled(boolean enabled) {
        this.whatsappNotificationsEnabled = enabled;
    }

    public String getSecurityPhone() {
        return securityPhone;
    }

    public void setSecurityPhone(String securityPhone) {
        this.securityPhone = securityPhone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ------------------------------------------------------------
    // Bloqueo por intentos
    // ------------------------------------------------------------

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public int getFailedPasswordAttempts() {
        return failedPasswordAttempts;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    /** Suma un fallo de contrasena. Devuelve true si con este fallo se bloquea. */
    public boolean registerPasswordFailure(int maxAttempts, LocalDateTime lockUntil) {
        failedPasswordAttempts++;
        if (failedPasswordAttempts >= maxAttempts) {
            lock(lockUntil);
            return true;
        }
        return false;
    }

    /** Suma un fallo de PIN. Devuelve true si con este fallo se bloquea. */
    public boolean registerPinFailure(int maxAttempts, LocalDateTime lockUntil) {
        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            lock(lockUntil);
            return true;
        }
        return false;
    }

    public void resetPasswordFailures() {
        failedPasswordAttempts = 0;
    }

    public void resetPinFailures() {
        failedPinAttempts = 0;
    }

    /** Quita el bloqueo y reinicia los contadores (por ejemplo, al recuperar la cuenta). */
    public void unlock() {
        lockedUntil = null;
        failedPasswordAttempts = 0;
        failedPinAttempts = 0;
    }

    private void lock(LocalDateTime until) {
        lockedUntil = until;
        failedPasswordAttempts = 0;
        failedPinAttempts = 0;
    }
}
