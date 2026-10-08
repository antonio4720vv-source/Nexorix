package com.nexorix.auth;

import com.nexorix.user.DiditKycProvider;
import com.nexorix.user.KycProviderResponse;
import com.nexorix.user.KycStatus;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recuperar la contrasena y/o el PIN.
 *
 *  1. La persona escribe su usuario o correo.
 *  2. Le enviamos un codigo de 6 digitos al correo.
 *  3. Escribe el codigo.
 *  4. Verifica su identidad en Didit (documento + selfie). El documento
 *     debe ser el de la cuenta.
 *  5. Elige una contrasena nueva y/o un PIN nuevo.
 *
 * Dos factores independientes: tener el correo NO basta (falta la
 * identidad), y tener el documento NO basta (falta el correo).
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    public static final Duration IDENTITY_LIFETIME = Duration.ofMinutes(30);
    public static final Duration RESEND_WAIT = Duration.ofSeconds(60);
    public static final int MAX_CODE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RecoveryRequestRepository recoveryRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryMailer mailer;
    private final DiditKycProvider didit;
    private final boolean documentCheck;
    private final SecureRandom random = new SecureRandom();

    public RecoveryService(
            UserRepository userRepository,
            RecoveryRequestRepository recoveryRepository,
            PasswordEncoder passwordEncoder,
            RecoveryMailer mailer,
            DiditKycProvider didit,
            @Value("${nexorix.recovery.document-check:true}") boolean documentCheck
    ) {
        this.userRepository = userRepository;
        this.recoveryRepository = recoveryRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.didit = didit;
        this.documentCheck = documentCheck;

        if (!documentCheck) {
            log.warn("ATENCION: la revision del documento en la recuperacion esta DESACTIVADA. "
                    + "Usalo solo en Sandbox, nunca en produccion.");
        }
    }

    // ============================================================
    // 1-2. PEDIR CODIGO
    // ============================================================

    /**
     * Devuelve el id de la solicitud, o vacio si no existe la cuenta.
     * El controlador responde lo mismo en ambos casos, para que nadie
     * pueda averiguar que usuarios o correos estan registrados.
     */
    @Transactional
    public Optional<Long> start(String identifier) {

        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String clean = identifier.trim();
        Optional<User> found = clean.contains("@")
                ? userRepository.findByEmail(clean.toLowerCase(Locale.ROOT))
                : userRepository.findByUsername(clean);

        if (found.isEmpty() || !found.get().isActive()) {
            return Optional.empty();
        }

        User user = found.get();
        LocalDateTime now = LocalDateTime.now();

        List<RecoveryRequest> previous =
                recoveryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Evita mandar correos sin parar: maximo uno por minuto.
        for (RecoveryRequest request : previous) {
            if (request.isOpen()
                    && RecoveryRequest.CODE_SENT.equals(request.getStatus())
                    && request.getCreatedAt().plus(RESEND_WAIT).isAfter(now)) {
                return Optional.of(request.getId());
            }
        }

        // Solo puede haber una solicitud abierta por usuario.
        previous.stream().filter(RecoveryRequest::isOpen).forEach(RecoveryRequest::cancel);

        String code = String.format("%06d", random.nextInt(1_000_000));

        RecoveryRequest request = recoveryRepository.save(new RecoveryRequest(
                user,
                passwordEncoder.encode(code),
                now.plus(CODE_LIFETIME)
        ));

        mailer.sendRecoveryCode(user.getEmail(), user.getName(), code);

        return Optional.of(request.getId());
    }

    // ============================================================
    // 3. REVISAR EL CODIGO
    // ============================================================

    @Transactional(noRollbackFor = RecoveryException.class)
    public void verifyCode(Long requestId, String code) {

        RecoveryRequest request = open(requestId);

        if (!RecoveryRequest.CODE_SENT.equals(request.getStatus())) {
            throw new RecoveryException("Este paso no corresponde. Empieza de nuevo.", true);
        }

        int attempts = request.registerCodeAttempt();

        if (code != null && code.trim().matches("\\d{6}")
                && passwordEncoder.matches(code.trim(), request.getCodeHash())) {
            request.codeVerified(LocalDateTime.now().plus(IDENTITY_LIFETIME));
            recoveryRepository.save(request);
            return;
        }

        if (attempts >= MAX_CODE_ATTEMPTS) {
            request.cancel();
            recoveryRepository.save(request);
            throw new RecoveryException(
                    "Demasiados intentos. Pide un código nuevo.", true);
        }

        recoveryRepository.save(request);

        int left = MAX_CODE_ATTEMPTS - attempts;
        throw new RecoveryException("Código incorrecto. Te quedan " + left
                + (left == 1 ? " intento." : " intentos."), false);
    }

    // ============================================================
    // 4. VERIFICAR LA IDENTIDAD (DIDIT)
    // ============================================================

    @Transactional(noRollbackFor = RecoveryException.class)
    public String startIdentity(Long requestId) {

        RecoveryRequest request = open(requestId);
        String status = request.getStatus();

        // Si ya habia una verificacion en curso, se reutiliza.
        if (RecoveryRequest.IDENTITY_PENDING.equals(status)
                && request.getVerificationUrl() != null) {
            return request.getVerificationUrl();
        }

        if (!RecoveryRequest.CODE_VERIFIED.equals(status)
                && !RecoveryRequest.IDENTITY_DECLINED.equals(status)) {
            throw new RecoveryException("Primero escribe el código que te enviamos.", true);
        }

        User user = request.getUser();

        KycProviderResponse response =
                didit.startRecoverySession(user.getPublicId(), user.getCedula());

        request.identityStarted(response.getVerificationReference(), response.getVerificationUrl());
        recoveryRepository.save(request);

        return response.getVerificationUrl();
    }

    /**
     * Webhook de Didit. Vacio si la sesion NO es de una recuperacion.
     */
    @Transactional
    public Optional<Boolean> processWebhook(String sessionId, String diditStatus, JsonNode decision) {

        if (sessionId == null) {
            return Optional.empty();
        }

        Optional<RecoveryRequest> found = recoveryRepository.findByDiditSessionId(sessionId);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        RecoveryRequest request = found.get();

        if (!RecoveryRequest.IDENTITY_PENDING.equals(request.getStatus()) || diditStatus == null) {
            return Optional.of(false);
        }

        String result = switch (diditStatus.trim().toLowerCase(Locale.ROOT)) {
            case "approved" -> RecoveryRequest.IDENTITY_APPROVED;
            case "declined", "expired", "abandoned" -> RecoveryRequest.IDENTITY_DECLINED;
            default -> null; // aun no termina
        };

        if (result == null) {
            return Optional.of(false);
        }

        // Didit aprueba a cualquier persona real con SU documento.
        // Debe ser el documento de ESTA cuenta.
        if (RecoveryRequest.IDENTITY_APPROVED.equals(result)
                && documentCheck
                && !documentBelongsToUser(decision, request.getUser())) {

            log.warn("Recuperacion RECHAZADA: el documento de la sesion {} no es el de la cuenta.",
                    sessionId);
            result = RecoveryRequest.IDENTITY_DECLINED;
        }

        request.setStatus(result);
        recoveryRepository.save(request);

        log.info("Recuperacion de cuenta. Sesion: {} | Resultado: {}", sessionId, result);
        return Optional.of(true);
    }

    // ============================================================
    // 5. CAMBIAR CONTRASENA Y/O PIN
    // ============================================================

    @Transactional(noRollbackFor = RecoveryException.class)
    public void reset(
            Long requestId,
            String newPassword,
            String newPasswordConfirm,
            String newPin,
            String newPinConfirm
    ) {
        RecoveryRequest request = open(requestId);

        if (!RecoveryRequest.IDENTITY_APPROVED.equals(request.getStatus())) {
            throw new RecoveryException("Primero debes verificar tu identidad.", false);
        }

        boolean changePassword = newPassword != null && !newPassword.isEmpty();
        boolean changePin = newPin != null && !newPin.isBlank();

        if (!changePassword && !changePin) {
            throw new RecoveryException("Escribe una contraseña nueva, un PIN nuevo o ambos.", false);
        }

        User user = request.getUser();

        try {
            if (changePassword) {
                UserService.validatePassword(newPassword, user.getUsername());

                if (!newPassword.equals(newPasswordConfirm)) {
                    throw new IllegalArgumentException("Las dos contraseñas no coinciden.");
                }
                if (user.getPasswordHash() != null
                        && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                    throw new IllegalArgumentException(
                            "La contraseña nueva debe ser diferente a la anterior.");
                }
            }

            if (changePin) {
                if (user.getKycStatus() != KycStatus.VERIFIED) {
                    throw new IllegalArgumentException(
                            "Primero debes completar la verificación de identidad de tu cuenta.");
                }

                AuthService.validatePin(newPin);

                if (!newPin.equals(newPinConfirm)) {
                    throw new IllegalArgumentException("Los dos PIN no coinciden.");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new RecoveryException(exception.getMessage(), false);
        }

        if (changePassword) {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        if (changePin) {
            user.setPinHash(passwordEncoder.encode(newPin));
        }

        user.unlock();
        userRepository.save(user);

        request.complete();
        recoveryRepository.save(request);

        log.info("Cuenta {} recuperada. Contrasena: {} | PIN: {}",
                user.getUsername(), changePassword, changePin);
    }

    // ============================================================
    // ESTADO Y AYUDAS
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> state(Long requestId) {

        RecoveryRequest request = open(requestId);
        User user = request.getUser();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", request.getStatus());
        state.put("email", RecoveryMailer.maskEmail(user.getEmail()));
        state.put("canSetPin", user.getKycStatus() == KycStatus.VERIFIED);
        return state;
    }

    @Transactional
    public void cancel(Long requestId) {
        recoveryRepository.findById(requestId).ifPresent(request -> {
            if (request.isOpen()) {
                request.cancel();
                recoveryRepository.save(request);
            }
        });
    }

    /** La solicitud existe, sigue abierta y no ha vencido. */
    private RecoveryRequest open(Long requestId) {

        if (requestId == null) {
            // Sin solicitud (por ejemplo, la cuenta no existe): mismo mensaje
            // que un codigo incorrecto, para no revelar nada.
            throw new RecoveryException("Código incorrecto o vencido.", false);
        }

        RecoveryRequest request = recoveryRepository.findById(requestId).orElse(null);

        if (request == null || !request.isOpen()) {
            throw new RecoveryException("La solicitud ya no es válida. Empieza de nuevo.", true);
        }

        if (request.isExpired(LocalDateTime.now())) {
            request.cancel();
            recoveryRepository.save(request);
            throw new RecoveryException("La solicitud venció. Empieza de nuevo.", true);
        }

        return request;
    }

    /**
     * true si la decision no trae documento o si alguno coincide con la
     * cedula del usuario (se comparan solo los digitos).
     */
    static boolean documentBelongsToUser(JsonNode decision, User user) {

        if (decision == null || decision.isNull() || decision.isMissingNode()) {
            return false; // sin decision no hay como comprobar el documento
        }

        JsonNode documents = decision.path("id_verifications");

        if (!documents.isArray() || documents.isEmpty()) {
            return false;
        }

        String expected = digits(user.getCedula());

        for (JsonNode document : documents) {
            String number = digits(document.path("document_number").asText(""));
            String personal = digits(document.path("personal_number").asText(""));

            if (!expected.isEmpty() && (expected.equals(number) || expected.equals(personal))) {
                return true;
            }
        }

        return false;
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
