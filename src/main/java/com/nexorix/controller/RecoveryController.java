package com.nexorix.controller;

import com.nexorix.auth.RecoveryException;
import com.nexorix.auth.RecoveryService;
import com.nexorix.dto.RecoveryCodeRequest;
import com.nexorix.dto.RecoveryResetRequest;
import com.nexorix.dto.RecoveryStartRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recuperar contrasena y/o PIN. El id de la solicitud se guarda en la
 * sesion del servidor: el navegador nunca lo ve ni lo puede cambiar.
 */
@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

    private static final Logger log = LoggerFactory.getLogger(RecoveryController.class);

    private static final String RECOVERY_ID = "recovery.id";

    private static final String GENERIC_SENT =
            "Si los datos corresponden a una cuenta, enviamos un código a su correo.";

    private final RecoveryService recoveryService;

    public RecoveryController(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(
            @RequestBody RecoveryStartRequest body,
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(true);
        session.removeAttribute(RECOVERY_ID);

        recoveryService.start(body.identifier())
                .ifPresent(id -> session.setAttribute(RECOVERY_ID, id));

        // La misma respuesta exista o no la cuenta.
        return Map.of("message", GENERIC_SENT);
    }

    @PostMapping("/code")
    public Map<String, Object> code(@RequestBody RecoveryCodeRequest body, HttpServletRequest request) {
        recoveryService.verifyCode(currentId(request), body.code());
        return Map.of("ok", true);
    }

    @PostMapping("/identity/start")
    public ResponseEntity<Map<String, Object>> startIdentity(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(Map.of("url", recoveryService.startIdentity(currentId(request))));
        } catch (RecoveryException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("No fue posible iniciar la verificacion para recuperar la cuenta: {}",
                    exception.getMessage());
            return ResponseEntity.status(502).body(Map.of("error",
                    "No fue posible iniciar la verificación de identidad. Intenta en unos minutos."));
        }
    }

    @GetMapping("/state")
    public Map<String, Object> state(HttpServletRequest request) {
        return recoveryService.state(currentId(request));
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody RecoveryResetRequest body, HttpServletRequest request) {

        Long id = currentId(request);

        recoveryService.reset(id, body.newPassword(), body.newPasswordConfirm(),
                body.newPin(), body.newPinConfirm());

        request.getSession().removeAttribute(RECOVERY_ID);
        return Map.of("ok", true);
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(HttpServletRequest request) {
        Long id = currentId(request);
        if (id != null) {
            recoveryService.cancel(id);
            request.getSession().removeAttribute(RECOVERY_ID);
        }
        return Map.of("ok", true);
    }

    @ExceptionHandler(RecoveryException.class)
    public ResponseEntity<Map<String, Object>> handle(RecoveryException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", exception.getMessage(),
                "restart", exception.isRestart()
        ));
    }

    private Long currentId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(RECOVERY_ID);
    }
}
