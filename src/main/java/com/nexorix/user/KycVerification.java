package com.nexorix.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_verifications")
public class KycVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String documentType;

    @Column(nullable = false, length = 100)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Column(length = 100)
    private String provider;

    @Column(length = 150)
    private String verificationReference;

    @Column(length = 1000)
    private String verificationUrl;

    @Column(length = 500)
    private String resultMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    protected KycVerification() {
    }

    public KycVerification(
            User user,
            String documentType,
            String documentNumber,
            KycStatus status,
            String provider
    ) {
        this.user = user;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.status = status;
        this.provider = provider;
        this.startedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public KycStatus getStatus() {
        return status;
    }

    public void setStatus(KycStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public String getVerificationReference() {
        return verificationReference;
    }

    public void setVerificationReference(
            String verificationReference
    ) {
        this.verificationReference = verificationReference;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public void setVerificationUrl(
            String verificationUrl
    ) {
        this.verificationUrl = verificationUrl;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}