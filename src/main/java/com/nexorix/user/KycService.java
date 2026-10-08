package com.nexorix.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class KycService {

    private static final Logger log =
            LoggerFactory.getLogger(KycService.class);

    private final KycVerificationRepository kycVerificationRepository;
    private final UserRepository userRepository;
    private final KycProvider kycProvider;

    public KycService(
            KycVerificationRepository kycVerificationRepository,
            UserRepository userRepository,
            KycProvider kycProvider
    ) {
        this.kycVerificationRepository = kycVerificationRepository;
        this.userRepository = userRepository;
        this.kycProvider = kycProvider;
    }

    // ============================================================
    // INICIAR VERIFICACION
    // ============================================================

    @Transactional
    public KycVerification startVerification(
            String username,
            String documentType,
            String documentNumber
    ) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("Usuario no encontrado")
                );

        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo de documento es obligatorio."
            );
        }

        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "El número de documento es obligatorio."
            );
        }

        KycVerification currentVerification =
                kycVerificationRepository
                        .findFirstByUserUsernameOrderByStartedAtDesc(username)
                        .orElse(null);

        if (currentVerification != null &&
                (currentVerification.getStatus() == KycStatus.PENDING ||
                        currentVerification.getStatus() == KycStatus.IN_PROGRESS)) {

            throw new IllegalStateException(
                    "Ya existe una verificación KYC en proceso."
            );
        }

        String normalizedDocumentType =
                documentType.trim().toUpperCase();

        String normalizedDocumentNumber =
                documentNumber.trim();

        KycProviderResponse providerResponse =
                kycProvider.startVerification(
                        normalizedDocumentType,
                        normalizedDocumentNumber,
                        user.getPublicId()
                );

        KycStatus providerStatus;

        try {
            providerStatus = KycStatus.valueOf(providerResponse.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "El proveedor KYC devolvió un estado no reconocido: "
                            + providerResponse.getStatus()
            );
        }

        KycVerification verification =
                new KycVerification(
                        user,
                        normalizedDocumentType,
                        normalizedDocumentNumber,
                        providerStatus,
                        providerResponse.getProvider()
                );

        verification.setVerificationReference(
                providerResponse.getVerificationReference()
        );

        verification.setVerificationUrl(
                providerResponse.getVerificationUrl()
        );

        verification.setResultMessage(
                providerResponse.getResultMessage()
        );

        if (isFinal(providerStatus)) {
            verification.setCompletedAt(LocalDateTime.now());
        }

        user.setKycStatus(providerStatus);

        userRepository.save(user);

        return kycVerificationRepository.save(verification);
    }

    // ============================================================
    // WEBHOOK DIDIT
    // ============================================================

    /**
     * Procesa un webhook status.updated de Didit.
     *
     * Devuelve true si se modifico una verificacion de Nexorix,
     * false si el webhook se recibio pero no cambio nada
     * (prueba, sesion desconocida, estado desconocido o
     * webhook atrasado).
     */
    @Transactional
    public boolean processDiditWebhook(
            String sessionId,
            String diditStatus,
            boolean testWebhook
    ) {

        if (testWebhook) {
            log.info("Webhook de prueba de Didit recibido. "
                    + "No se modificará ningún usuario de Nexorix.");
            return false;
        }

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "El webhook de Didit no contiene session_id."
            );
        }

        if (diditStatus == null || diditStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "El webhook de Didit no contiene status."
            );
        }

        KycVerification verification =
                kycVerificationRepository
                        .findByVerificationReference(sessionId)
                        .orElse(null);

        if (verification == null) {
            log.warn("No existe una verificación Nexorix asociada "
                    + "al session_id de Didit: {}", sessionId);
            return false;
        }

        KycStatus newStatus = mapDiditStatus(diditStatus);

        if (newStatus == null) {
            // Estado nuevo o desconocido: lo registramos pero NO
            // fallamos, para que Didit no reintente el envio en bucle.
            log.warn("Estado de Didit no reconocido: '{}'. "
                    + "No se modifica la verificación.", diditStatus);
            return false;
        }

        KycStatus currentStatus = verification.getStatus();

        // Los webhooks pueden llegar desordenados. Si la verificacion
        // ya termino, un "In Progress" atrasado no debe deshacerla.
        // La unica excepcion es "Resubmitted": el usuario volvio a
        // enviar sus documentos y la verificacion se reabre.
        if (isFinal(currentStatus)
                && newStatus == KycStatus.IN_PROGRESS
                && !isResubmitted(diditStatus)) {

            log.info("Webhook atrasado ignorado. Sesión {}: estado actual {}, "
                    + "Didit envió '{}'.", sessionId, currentStatus, diditStatus);
            return false;
        }

        verification.setStatus(newStatus);

        verification.setResultMessage(
                "Estado recibido desde Didit: " + diditStatus.trim()
        );

        if (isFinal(newStatus)) {
            if (verification.getCompletedAt() == null) {
                verification.setCompletedAt(LocalDateTime.now());
            }
        } else {
            verification.setCompletedAt(null);
        }

        kycVerificationRepository.save(verification);

        User user = verification.getUser();
        user.setKycStatus(newStatus);
        userRepository.save(user);

        log.info("KYC Nexorix actualizado. Usuario: {} | Didit: '{}' | "
                        + "Nuevo estado: {}",
                user.getUsername(), diditStatus.trim(), newStatus);

        return true;
    }

    /**
     * Traduce el estado de Didit al estado de Nexorix.
     * Devuelve null si el estado no se reconoce.
     */
    KycStatus mapDiditStatus(String diditStatus) {

        String normalized =
                diditStatus.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {

            case "approved" ->
                    KycStatus.VERIFIED;

            case "declined" ->
                    KycStatus.REJECTED;

            case "in review" ->
                    KycStatus.MANUAL_REVIEW;

            case "not started",
                 "in progress",
                 "waiting",
                 "resubmitted" ->
                    KycStatus.IN_PROGRESS;

            // La sesion vencio o el usuario la abandono.
            // Se marca como REJECTED para que pueda iniciar una nueva.
            // El motivo real queda guardado en resultMessage.
            case "expired",
                 "abandoned",
                 "kyc expired" ->
                    KycStatus.REJECTED;

            default ->
                    null;
        };
    }

    private boolean isFinal(KycStatus status) {
        return status == KycStatus.VERIFIED
                || status == KycStatus.REJECTED
                || status == KycStatus.MANUAL_REVIEW;
    }

    private boolean isResubmitted(String diditStatus) {
        return "resubmitted".equals(
                diditStatus.trim().toLowerCase(Locale.ROOT)
        );
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    @Transactional(readOnly = true)
    public KycVerification getLatestVerification(String username) {
        return kycVerificationRepository
                .findFirstByUserUsernameOrderByStartedAtDesc(username)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<KycVerification> getVerificationHistory(String username) {
        return kycVerificationRepository
                .findByUserUsernameOrderByStartedAtDesc(username);
    }
}
