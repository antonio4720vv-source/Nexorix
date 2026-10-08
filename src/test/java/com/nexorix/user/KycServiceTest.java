package com.nexorix.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KycServiceTest {

    private static final String SESSION_ID = "sesion-de-prueba";

    private KycVerificationRepository kycVerificationRepository;
    private UserRepository userRepository;
    private KycService kycService;

    private User user;
    private KycVerification verification;

    @BeforeEach
    void setUp() {
        kycVerificationRepository = mock(KycVerificationRepository.class);
        userRepository = mock(UserRepository.class);
        KycProvider kycProvider = mock(KycProvider.class);

        kycService = new KycService(
                kycVerificationRepository, userRepository, kycProvider
        );

        user = new User("Ana Prueba", "anaprueba1",
                "ana@nexorix.com", "1000000001", "hash");

        verification = new KycVerification(
                user, "CC", "1000000001", KycStatus.IN_PROGRESS, "DIDIT"
        );
        verification.setVerificationReference(SESSION_ID);
        user.setKycStatus(KycStatus.IN_PROGRESS);
    }

    private void sesionExiste() {
        when(kycVerificationRepository.findByVerificationReference(SESSION_ID))
                .thenReturn(Optional.of(verification));
    }

    @ParameterizedTest
    @CsvSource({
            "Approved, VERIFIED",
            "Declined, REJECTED",
            "In Review, MANUAL_REVIEW",
            "Not Started, IN_PROGRESS",
            "In Progress, IN_PROGRESS",
            "Resubmitted, IN_PROGRESS",
            "Expired, REJECTED",
            "Abandoned, REJECTED",
            "approved, VERIFIED"
    })
    void traduceLosEstadosDeDidit(String diditStatus, KycStatus esperado) {
        assertThat(kycService.mapDiditStatus(diditStatus)).isEqualTo(esperado);
    }

    @Test
    void notStartedYaNoFalla() {
        sesionExiste();

        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "Not Started", false);

        assertThat(procesado).isTrue();
        assertThat(verification.getStatus()).isEqualTo(KycStatus.IN_PROGRESS);
    }

    @Test
    void approvedMarcaUsuarioYVerificacionComoVerified() {
        sesionExiste();

        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "Approved", false);

        assertThat(procesado).isTrue();
        assertThat(verification.getStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(verification.getCompletedAt()).isNotNull();
        assertThat(user.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        verify(userRepository).save(user);
    }

    @Test
    void estadoDesconocidoNoFallaNiModificaNada() {
        sesionExiste();

        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "Estado Raro", false);

        assertThat(procesado).isFalse();
        assertThat(verification.getStatus()).isEqualTo(KycStatus.IN_PROGRESS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void inProgressAtrasadoNoDeshaceUnaVerificacionAprobada() {
        sesionExiste();
        kycService.processDiditWebhook(SESSION_ID, "Approved", false);

        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "In Progress", false);

        assertThat(procesado).isFalse();
        assertThat(verification.getStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(user.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
    }

    @Test
    void resubmittedSiReabreUnaVerificacionRechazada() {
        sesionExiste();
        kycService.processDiditWebhook(SESSION_ID, "Declined", false);

        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "Resubmitted", false);

        assertThat(procesado).isTrue();
        assertThat(verification.getStatus()).isEqualTo(KycStatus.IN_PROGRESS);
        assertThat(verification.getCompletedAt()).isNull();
    }

    @Test
    void webhookDePruebaNoModificaNada() {
        boolean procesado =
                kycService.processDiditWebhook(SESSION_ID, "Approved", true);

        assertThat(procesado).isFalse();
        verify(kycVerificationRepository, never()).save(any());
    }

    @Test
    void sesionDesconocidaNoModificaNada() {
        when(kycVerificationRepository.findByVerificationReference("otra"))
                .thenReturn(Optional.empty());

        boolean procesado =
                kycService.processDiditWebhook("otra", "Approved", false);

        assertThat(procesado).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void sinSessionIdLanzaError() {
        assertThatThrownBy(() ->
                kycService.processDiditWebhook("", "Approved", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
