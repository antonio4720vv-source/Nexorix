package com.nexorix.auth;

import com.nexorix.user.KycStatus;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String PASSWORD = "Nexorix#2026";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // rapido en pruebas

    private UserRepository userRepository;
    private AuthService authService;
    private User ana;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = new AuthService(userRepository, encoder);

        ana = new User("Ana", "anaprueba9", "ana9@nexorix.com", "1000000009",
                encoder.encode(PASSWORD));
        ReflectionTestUtils.setField(ana, "id", 1L);

        when(userRepository.findByUsername("anaprueba9")).thenReturn(Optional.of(ana));
        when(userRepository.findById(1L)).thenReturn(Optional.of(ana));
    }

    private void fallarContrasena(int veces) {
        for (int i = 0; i < veces; i++) {
            try {
                authService.checkPassword("anaprueba9", "Mala#Clave99");
            } catch (AuthException ignored) {
                // esperado
            }
        }
    }

    // ------------------------------------------------------------
    // Contrasena
    // ------------------------------------------------------------

    @Test
    void conLaContrasenaCorrectaDevuelveElUsuario() {
        assertThat(authService.checkPassword("anaprueba9", PASSWORD)).isSameAs(ana);
    }

    @Test
    void conContrasenaIncorrectaDaUnMensajeGenerico() {
        assertThatThrownBy(() -> authService.checkPassword("anaprueba9", "Mala#Clave99"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Usuario o contraseña incorrectos.");
    }

    @Test
    void unUsuarioQueNoExisteDaElMismoMensaje() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.checkPassword("nadie", PASSWORD))
                .isInstanceOf(AuthException.class)
                .hasMessage("Usuario o contraseña incorrectos.");
    }

    // ------------------------------------------------------------
    // Bloqueo por contrasenas incorrectas
    // ------------------------------------------------------------

    @Test
    void cuatroFallosTodaviaNoBloquean() {
        fallarContrasena(4);

        assertThat(ana.isLocked(LocalDateTime.now())).isFalse();
        assertThat(authService.checkPassword("anaprueba9", PASSWORD)).isSameAs(ana);
    }

    @Test
    void elQuintoFalloBloqueaLaCuenta() {
        fallarContrasena(4);

        assertThatThrownBy(() -> authService.checkPassword("anaprueba9", "Mala#Clave99"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("bloqueada");

        assertThat(ana.isLocked(LocalDateTime.now())).isTrue();
    }

    @Test
    void bloqueadaNoEntraNiConLaContrasenaCorrecta() {
        fallarContrasena(5);

        assertThatThrownBy(() -> authService.checkPassword("anaprueba9", PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void cuandoPasaElBloqueoPuedeVolverAEntrar() {
        fallarContrasena(5);
        ReflectionTestUtils.setField(ana, "lockedUntil", LocalDateTime.now().minusMinutes(1));

        assertThat(authService.checkPassword("anaprueba9", PASSWORD)).isSameAs(ana);
    }

    @Test
    void entrarBienReiniciaElContador() {
        fallarContrasena(4);
        authService.checkPassword("anaprueba9", PASSWORD);
        fallarContrasena(4);

        assertThat(ana.isLocked(LocalDateTime.now())).isFalse();
    }

    // ------------------------------------------------------------
    // Bloqueo por PIN incorrectos
    // ------------------------------------------------------------

    @Test
    void elPinIncorrectoDiceCuantosIntentosQuedan() {
        ana.setKycStatus(KycStatus.VERIFIED);
        ana.setPinHash(encoder.encode("482913"));

        assertThatThrownBy(() -> authService.verifyPin(1L, "000001"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Te quedan 4 intentos");
    }

    @Test
    void cincoPinIncorrectosBloqueanLaCuenta() {
        ana.setKycStatus(KycStatus.VERIFIED);
        ana.setPinHash(encoder.encode("482913"));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.verifyPin(1L, "000001"))
                    .isNotInstanceOf(AccountLockedException.class);
        }

        assertThatThrownBy(() -> authService.verifyPin(1L, "000001"))
                .isInstanceOf(AccountLockedException.class);

        // Bloqueada: ni el PIN correcto sirve.
        assertThatThrownBy(() -> authService.verifyPin(1L, "482913"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void elPinCorrectoNoLanzaError() {
        ana.setKycStatus(KycStatus.VERIFIED);
        ana.setPinHash(encoder.encode("482913"));

        assertThatCode(() -> authService.verifyPin(1L, "482913")).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------
    // Pasos y PIN
    // ------------------------------------------------------------

    @Test
    void ordenDeLosPasos() {
        assertThat(authService.stageAfterPassword(ana)).isEqualTo(LoginStage.DONE);

        ana.setKycStatus(KycStatus.VERIFIED);
        assertThat(authService.stageAfterPassword(ana)).isEqualTo(LoginStage.CREATE_PIN);

        ana.setPinHash(encoder.encode("482913"));
        assertThat(authService.stageAfterPassword(ana)).isEqualTo(LoginStage.PIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"111111", "123456", "654321", "345678", "12345", "12a456", ""})
    void rechazaPinesInvalidosOFacilesDeAdivinar(String pin) {
        assertThatThrownBy(() -> AuthService.validatePin(pin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creaElPinGuardandoSoloSuHuella() {
        ana.setKycStatus(KycStatus.VERIFIED);

        authService.createPin(1L, "482913", "482913");

        assertThat(ana.getPinHash()).isNotEqualTo("482913");
        assertThatCode(() -> authService.verifyPin(1L, "482913")).doesNotThrowAnyException();
    }

    @Test
    void noCreaElPinSiLosDosNoCoinciden() {
        ana.setKycStatus(KycStatus.VERIFIED);

        assertThatThrownBy(() -> authService.createPin(1L, "482913", "482914"))
                .hasMessageContaining("no coinciden");
    }
}
