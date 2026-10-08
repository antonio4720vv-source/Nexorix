package com.nexorix.auth;

import com.nexorix.user.KycStatus;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Reglas del inicio de sesion de Nexorix:
 *
 *   usuario + contrasena  ->  PIN de 6 digitos  ->  dentro
 *
 * Proteccion: 5 contrasenas o 5 PIN incorrectos seguidos bloquean
 * la cuenta 15 minutos. El contador se guarda en la base de datos,
 * asi que no sirve cerrar el navegador y volver a empezar.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final String GENERIC_LOGIN_ERROR = "Usuario o contraseña incorrectos.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Huella falsa para comparar cuando el usuario no existe. Asi la
     * respuesta tarda lo mismo exista o no el usuario, y nadie puede
     * adivinar que cuentas existen midiendo el tiempo.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("nexorix-usuario-inexistente");
    }

    // ============================================================
    // PASO 1: USUARIO + CONTRASENA
    // ============================================================

    /**
     * noRollbackFor: aunque se lance el error de "contrasena incorrecta",
     * el contador de fallos SI debe quedar guardado.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public User checkPassword(String username, String password) {

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthException(GENERIC_LOGIN_ERROR);
        }

        User user = userRepository.findByUsername(username.trim()).orElse(null);

        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyHash); // mismo tiempo de respuesta
            throw new AuthException(GENERIC_LOGIN_ERROR);
        }

        LocalDateTime now = LocalDateTime.now();

        // Bloqueada: ni siquiera se revisa la contrasena.
        if (user.isLocked(now)) {
            throw new AccountLockedException(lockedMessage(user, now));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {

            boolean locked = user.registerPasswordFailure(
                    MAX_FAILED_ATTEMPTS, now.plus(LOCK_DURATION));
            userRepository.save(user);

            if (locked) {
                log.warn("Cuenta {} bloqueada por contrasenas incorrectas.", user.getUsername());
                throw new AccountLockedException(lockedMessage(user, now));
            }

            throw new AuthException(GENERIC_LOGIN_ERROR);
        }

        if (!user.isActive()) {
            throw new AuthException("Esta cuenta está desactivada.");
        }

        if (user.getFailedPasswordAttempts() > 0) {
            user.resetPasswordFailures();
            userRepository.save(user);
        }

        return user;
    }

    /** Siguiente paso despues de la contrasena. */
    public LoginStage stageAfterPassword(User user) {

        if (user.getKycStatus() != KycStatus.VERIFIED) {
            return LoginStage.DONE; // entra para terminar la verificacion
        }

        return user.hasPin() ? LoginStage.PIN : LoginStage.CREATE_PIN;
    }

    // ============================================================
    // PASO 2: PIN
    // ============================================================

    /**
     * Revisa el PIN. Si es incorrecto lanza AuthException con los intentos
     * que quedan, o AccountLockedException si con este fallo se bloquea.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public void verifyPin(Long userId, String pin) {

        User user = findUser(userId);
        LocalDateTime now = LocalDateTime.now();

        if (user.isLocked(now)) {
            throw new AccountLockedException(lockedMessage(user, now));
        }

        if (pin != null && user.hasPin() && passwordEncoder.matches(pin, user.getPinHash())) {
            if (user.getFailedPinAttempts() > 0) {
                user.resetPinFailures();
                userRepository.save(user);
            }
            return;
        }

        boolean locked = user.registerPinFailure(MAX_FAILED_ATTEMPTS, now.plus(LOCK_DURATION));
        userRepository.save(user);

        if (locked) {
            log.warn("Cuenta {} bloqueada por PIN incorrectos.", user.getUsername());
            throw new AccountLockedException(lockedMessage(user, now));
        }

        int left = MAX_FAILED_ATTEMPTS - user.getFailedPinAttempts();
        throw new AuthException("PIN incorrecto. Te quedan " + left
                + (left == 1 ? " intento." : " intentos."));
    }

    @Transactional
    public void createPin(Long userId, String pin, String pinConfirm) {

        User user = findUser(userId);

        if (user.hasPin()) {
            throw new IllegalArgumentException("Ya tienes un PIN creado.");
        }

        if (user.getKycStatus() != KycStatus.VERIFIED) {
            throw new IllegalArgumentException("Primero debes verificar tu identidad.");
        }

        validatePin(pin);

        if (!pin.equals(pinConfirm)) {
            throw new IllegalArgumentException("Los dos PIN no coinciden.");
        }

        user.setPinHash(passwordEncoder.encode(pin));
        userRepository.save(user);
    }

    /** 6 digitos, sin repetir el mismo numero ni secuencias obvias. */
    public static void validatePin(String pin) {

        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("El PIN debe tener exactamente 6 dígitos.");
        }

        boolean allSame = pin.chars().distinct().count() == 1;
        boolean ascending = "0123456789".contains(pin);
        boolean descending = "9876543210".contains(pin);

        if (allSame || ascending || descending) {
            throw new IllegalArgumentException("Ese PIN es muy fácil de adivinar. Elige otro.");
        }
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    @Transactional(readOnly = true)
    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Usuario no encontrado."));
    }

    static String lockedMessage(User user, LocalDateTime now) {
        long minutes = Math.max(1,
                Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
        return "Por seguridad, tu cuenta está bloqueada por " + minutes
                + (minutes == 1 ? " minuto" : " minutos")
                + " debido a varios intentos fallidos. Si olvidaste tu contraseña o tu PIN, "
                + "puedes recuperarlos.";
    }
}
