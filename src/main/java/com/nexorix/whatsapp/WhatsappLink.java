package com.nexorix.whatsapp;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * El numero de WhatsApp de una persona.
 *
 * Nexorix SOLO escribe a un numero verificado: para verificarlo, la persona
 * manda desde ese WhatsApp el codigo que le muestra la pagina. Asi nadie puede
 * poner el numero de otro y llenarlo de mensajes, y de paso se abre la ventana
 * de 24 horas en la que WhatsApp permite responder con texto libre.
 */
@Entity
@Table(name = "whatsapp_links")
public class WhatsappLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Solo digitos, con indicativo del pais (ej. 573001234567). */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean verified;

    /** Codigo de 6 digitos que la persona manda desde su WhatsApp. */
    @Column(name = "verification_code", length = 6)
    private String verificationCode;

    @Column(name = "code_expires_at")
    private LocalDateTime codeExpiresAt;

    /** La persona puede pausar las preguntas sin borrar el numero. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WhatsappLink() {
    }

    public WhatsappLink(User user, String phone) {
        this.user = user;
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPhone() {
        return phone;
    }

    /** Cambiar el numero obliga a verificarlo otra vez. */
    public void changePhone(String phone, String code, LocalDateTime expiresAt) {
        this.phone = phone;
        this.verified = false;
        this.verificationCode = code;
        this.codeExpiresAt = expiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isVerified() {
        return verified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public LocalDateTime getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public void markVerified() {
        this.verified = true;
        this.verificationCode = null;
        this.codeExpiresAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /** Verificado y con las preguntas activas. */
    public boolean canReceiveQuestions() {
        return verified && enabled;
    }
}
