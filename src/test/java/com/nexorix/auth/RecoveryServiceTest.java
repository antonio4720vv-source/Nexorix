package com.nexorix.auth;

import com.nexorix.user.DiditKycProvider;
import com.nexorix.user.KycProviderResponse;
import com.nexorix.user.KycStatus;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private UserRepository userRepository;
    private RecoveryRequestRepository recoveryRepository;
    private RecoveryMailer mailer;
    private DiditKycProvider didit;
    private RecoveryService service;

    private User ana;
    private final List<RecoveryRequest> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        recoveryRepository = mock(RecoveryRequestRepository.class);
        mailer = mock(RecoveryMailer.class);
        didit = mock(DiditKycProvider.class);

        service = new RecoveryService(userRepository, recoveryRepository, encoder,
                mailer, didit, true);

        ana = new User("Ana", "anaprueba9", "ana9@nexorix.com", "1000000009",
                encoder.encode("Nexorix#2026"));
        ReflectionTestUtils.setField(ana, "id", 1L);
        ana.setKycStatus(KycStatus.VERIFIED);
        ana.setPinHash(encoder.encode("482913"));

        when(userRepository.findByUsername("anaprueba9")).thenReturn(Optional.of(ana));
        when(userRepository.findByEmail("ana9@nexorix.com")).thenReturn(Optional.of(ana));

        // El repositorio "guarda" la solicitud y le pone id 10.
        when(recoveryRepository.save(any(RecoveryRequest.class))).thenAnswer(call -> {
            RecoveryRequest request = call.getArgument(0);
            if (request.getId() == null) {
                ReflectionTestUtils.setField(request, "id", 10L);
                saved.add(request);
            }
            return request;
        });
        when(recoveryRepository.findById(10L))
                .thenAnswer(call -> saved.stream().findFirst());
    }

    /** Pide el codigo y devuelve el que "llego al correo". */
    private String pedirCodigo() {
        service.start("anaprueba9");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendRecoveryCode(eq("ana9@nexorix.com"), eq("Ana"), code.capture());
        return code.getValue();
    }

    private void identidadAprobada() {
        String code = pedirCodigo();
        service.verifyCode(10L, code);
        when(didit.startRecoverySession(anyString(), anyString())).thenReturn(
                new KycProviderResponse("DIDIT", "IN_PROGRESS", "sess-1", "ok", "https://didit/x"));
        service.startIdentity(10L);
        when(recoveryRepository.findByDiditSessionId("sess-1"))
                .thenReturn(Optional.of(saved.get(0)));
        service.processWebhook("sess-1", "Approved", decisionConDocumento("1000000009"));
    }

    private JsonNode decisionConDocumento(String numero) {
        return JsonMapper.builder().build().readTree(
                "{\"id_verifications\":[{\"document_number\":\"" + numero + "\"}]}");
    }

    // ------------------------------------------------------------
    // Pedir el codigo
    // ------------------------------------------------------------

    @Test
    void unaCuentaQueNoExisteNoRecibeCorreo() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThat(service.start("nadie")).isEmpty();
        verify(mailer, never()).sendRecoveryCode(any(), any(), any());
    }

    @Test
    void enviaUnCodigoDeSeisDigitosAlCorreo() {
        String code = pedirCodigo();

        assertThat(code).matches("\\d{6}");
        // Se guarda la huella, nunca el codigo.
        assertThat(saved.get(0).getCodeHash()).isNotEqualTo(code);
    }

    @Test
    void tambienSePuedeBuscarPorCorreo() {
        assertThat(service.start("ANA9@nexorix.com")).contains(10L);
    }

    // ------------------------------------------------------------
    // Revisar el codigo
    // ------------------------------------------------------------

    @Test
    void conElCodigoCorrectoAvanza() {
        String code = pedirCodigo();

        service.verifyCode(10L, code);

        assertThat(saved.get(0).getStatus()).isEqualTo(RecoveryRequest.CODE_VERIFIED);
    }

    @Test
    void conCodigoIncorrectoDiceCuantosIntentosQuedan() {
        pedirCodigo();

        assertThatThrownBy(() -> service.verifyCode(10L, "000000"))
                .isInstanceOf(RecoveryException.class)
                .hasMessageContaining("Te quedan 4");
    }

    @Test
    void cincoCodigosIncorrectosCancelanLaSolicitud() {
        String code = pedirCodigo();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.verifyCode(10L, "000000"));
        }

        assertThatThrownBy(() -> service.verifyCode(10L, "000000"))
                .isInstanceOf(RecoveryException.class)
                .hasMessageContaining("Demasiados intentos");

        // Ya ni el codigo correcto sirve.
        assertThatThrownBy(() -> service.verifyCode(10L, code))
                .isInstanceOf(RecoveryException.class);
    }

    @Test
    void unCodigoVencidoNoSirve() {
        String code = pedirCodigo();
        ReflectionTestUtils.setField(saved.get(0), "expiresAt", LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.verifyCode(10L, code))
                .isInstanceOf(RecoveryException.class)
                .hasMessageContaining("venció");
    }

    // ------------------------------------------------------------
    // Identidad
    // ------------------------------------------------------------

    @Test
    void noSePuedeVerificarLaIdentidadSinElCodigo() {
        pedirCodigo();

        assertThatThrownBy(() -> service.startIdentity(10L))
                .isInstanceOf(RecoveryException.class);
        verify(didit, never()).startRecoverySession(anyString(), anyString());
    }

    @Test
    void conElDocumentoDeLaCuentaSeApruebaLaIdentidad() {
        identidadAprobada();

        assertThat(saved.get(0).getStatus()).isEqualTo(RecoveryRequest.IDENTITY_APPROVED);
    }

    @Test
    void conElDocumentoDeOtraPersonaSeRechazaAunqueDiditApruebe() {
        String code = pedirCodigo();
        service.verifyCode(10L, code);
        when(didit.startRecoverySession(anyString(), anyString())).thenReturn(
                new KycProviderResponse("DIDIT", "IN_PROGRESS", "sess-1", "ok", "https://didit/x"));
        service.startIdentity(10L);
        when(recoveryRepository.findByDiditSessionId("sess-1"))
                .thenReturn(Optional.of(saved.get(0)));

        service.processWebhook("sess-1", "Approved", decisionConDocumento("79888777"));

        assertThat(saved.get(0).getStatus()).isEqualTo(RecoveryRequest.IDENTITY_DECLINED);
    }

    @Test
    void unaSesionQueNoEsDeRecuperacionSeDejaAlKyc() {
        when(recoveryRepository.findByDiditSessionId("otra")).thenReturn(Optional.empty());

        assertThat(service.processWebhook("otra", "Approved", null)).isEmpty();
    }

    // ------------------------------------------------------------
    // Cambiar contrasena y/o PIN
    // ------------------------------------------------------------

    @Test
    void noSePuedeCambiarNadaSinVerificarLaIdentidad() {
        String code = pedirCodigo();
        service.verifyCode(10L, code);

        assertThatThrownBy(() -> service.reset(10L, "Nueva#Clave2026", "Nueva#Clave2026", null, null))
                .hasMessageContaining("verificar tu identidad");
    }

    @Test
    void cambiaLaContrasenaYDesbloqueaLaCuenta() {
        identidadAprobada();
        ReflectionTestUtils.setField(ana, "lockedUntil", LocalDateTime.now().plusMinutes(10));

        service.reset(10L, "Nueva#Clave2026", "Nueva#Clave2026", "", "");

        assertThat(encoder.matches("Nueva#Clave2026", ana.getPasswordHash())).isTrue();
        assertThat(encoder.matches("482913", ana.getPinHash())).isTrue(); // el PIN no cambio
        assertThat(ana.isLocked(LocalDateTime.now())).isFalse();
        assertThat(saved.get(0).getStatus()).isEqualTo(RecoveryRequest.COMPLETED);
    }

    @Test
    void cambiaSoloElPin() {
        identidadAprobada();

        service.reset(10L, "", "", "593024", "593024");

        assertThat(encoder.matches("593024", ana.getPinHash())).isTrue();
        assertThat(encoder.matches("Nexorix#2026", ana.getPasswordHash())).isTrue();
    }

    @Test
    void laContrasenaNuevaDebeCumplirLasReglas() {
        identidadAprobada();

        assertThatThrownBy(() -> service.reset(10L, "debil", "debil", null, null))
                .isInstanceOf(RecoveryException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void laContrasenaNuevaDebeSerDiferente() {
        identidadAprobada();

        assertThatThrownBy(() -> service.reset(10L, "Nexorix#2026", "Nexorix#2026", null, null))
                .hasMessageContaining("diferente");
    }

    @Test
    void unaSolicitudUsadaNoSePuedeReutilizar() {
        identidadAprobada();
        service.reset(10L, "Nueva#Clave2026", "Nueva#Clave2026", null, null);

        assertThatThrownBy(() -> service.reset(10L, "Otra#Clave2026", "Otra#Clave2026", null, null))
                .isInstanceOf(RecoveryException.class);
        verify(userRepository, times(1)).save(ana);
    }
}
