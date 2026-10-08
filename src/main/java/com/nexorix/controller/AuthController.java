package com.nexorix.controller;

import com.nexorix.auth.AccountLockedException;
import com.nexorix.auth.AuthException;
import com.nexorix.auth.AuthService;
import com.nexorix.auth.LoginStage;
import com.nexorix.auth.SessionLogin;
import com.nexorix.dto.LoginRequest;
import com.nexorix.dto.PinRequest;
import com.nexorix.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Inicio de sesion en dos pasos: usuario + contrasena, y luego PIN.
 * Los intentos fallidos se cuentan por usuario en la base de datos
 * (ver AuthService): 423 = cuenta bloqueada temporalmente.
 *
 * El avance se guarda en la sesion del servidor: nadie puede saltarse
 * el PIN cambiando la pagina.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String USER_ID = "auth.userId";
    private static final String STAGE = "auth.stage";
    private static final String STARTED_AT = "auth.startedAt";

    /** Tiempo maximo para escribir el PIN despues de la contrasena. */
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);

    private final AuthService authService;
    private final SessionLogin sessionLogin;

    public AuthController(AuthService authService, SessionLogin sessionLogin) {
        this.authService = authService;
        this.sessionLogin = sessionLogin;
    }

    /** Paso actual (sirve si la persona recarga la pagina). */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> state(HttpServletRequest request) {

        LoginStage stage = currentStage(request.getSession(false));

        if (stage == null) {
            return error(401, "No hay un inicio de sesión en curso.");
        }

        return ResponseEntity.ok(Map.of("next", stage.name()));
    }

    // ============================================================
    // PASO 1: USUARIO + CONTRASENA
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user;

        try {
            user = authService.checkPassword(body.username(), body.password());
        } catch (AccountLockedException exception) {
            return error(423, exception.getMessage());
        } catch (AuthException exception) {
            return error(401, exception.getMessage());
        }

        LoginStage next = authService.stageAfterPassword(user);

        if (next == LoginStage.DONE) {
            sessionLogin.logIn(user.getUsername(), request, response);
            return ResponseEntity.ok(Map.of("next", LoginStage.DONE.name()));
        }

        HttpSession session = request.getSession(true);
        clearPending(session);
        session.setAttribute(USER_ID, user.getId());
        session.setAttribute(STAGE, next.name());
        session.setAttribute(STARTED_AT, Instant.now());

        return ResponseEntity.ok(Map.of("next", next.name()));
    }

    // ============================================================
    // PASO 2: PIN
    // ============================================================

    @PostMapping("/pin")
    public ResponseEntity<Map<String, Object>> pin(
            @RequestBody PinRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);

        if (currentStage(session) != LoginStage.PIN) {
            return error(409, "Este paso no corresponde. Inicia sesión de nuevo.");
        }

        Long userId = (Long) session.getAttribute(USER_ID);

        try {
            authService.verifyPin(userId, body.pin());
        } catch (AccountLockedException exception) {
            // Bloqueada: debe empezar de nuevo (y esperar o recuperar la cuenta).
            clearPending(session);
            return ResponseEntity.status(423).body(Map.of(
                    "error", exception.getMessage(),
                    "restart", true
            ));
        } catch (AuthException exception) {
            return error(401, exception.getMessage());
        }

        finish(userId, request, response);
        return ResponseEntity.ok(Map.of("next", LoginStage.DONE.name()));
    }

    @PostMapping("/pin/create")
    public ResponseEntity<Map<String, Object>> createPin(
            @RequestBody PinRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);

        if (currentStage(session) != LoginStage.CREATE_PIN) {
            return error(409, "Este paso no corresponde. Inicia sesión de nuevo.");
        }

        Long userId = (Long) session.getAttribute(USER_ID);

        try {
            authService.createPin(userId, body.pin(), body.pinConfirm());
        } catch (IllegalArgumentException exception) {
            return error(400, exception.getMessage());
        }

        finish(userId, request, response);
        return ResponseEntity.ok(Map.of("next", LoginStage.DONE.name()));
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    private void finish(Long userId, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.findUser(userId);
        clearPending(request.getSession(false));
        sessionLogin.logIn(user.getUsername(), request, response);
    }

    private LoginStage currentStage(HttpSession session) {

        if (session == null || session.getAttribute(STAGE) == null) {
            return null;
        }

        Instant startedAt = (Instant) session.getAttribute(STARTED_AT);

        if (startedAt == null || startedAt.plus(LOGIN_WINDOW).isBefore(Instant.now())) {
            clearPending(session);
            return null;
        }

        return LoginStage.valueOf((String) session.getAttribute(STAGE));
    }

    private void clearPending(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(USER_ID);
        session.removeAttribute(STAGE);
        session.removeAttribute(STARTED_AT);
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
