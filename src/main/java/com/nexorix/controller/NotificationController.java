package com.nexorix.controller;

import com.nexorix.bank.Bank;
import com.nexorix.bank.BankCatalog;
import com.nexorix.notification.NotificationService;
import com.nexorix.notification.NotificationToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bancos reales y notificaciones del celular:
 *   GET    /api/banks                      lista de bancos y billeteras
 *   GET    /api/notifications/connection   estado de la conexion
 *   POST   /api/notifications/connection   crea la llave (se muestra una sola vez)
 *   DELETE /api/notifications/connection   la desactiva
 *   POST   /api/notifications/ingest       lo usa la app del celular (con la llave, sin sesion)
 */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/banks")
    public List<Map<String, Object>> banks() {
        return BankCatalog.all().stream().map(NotificationController::bankView).toList();
    }

    @GetMapping("/notifications/connection")
    public ResponseEntity<Map<String, Object>> connection() {
        String username = username();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        NotificationToken token = service.status(username).orElse(null);
        body.put("connected", token != null);
        if (token != null) {
            body.put("createdAt", token.getCreatedAt());
            body.put("lastUsedAt", token.getLastUsedAt());
            body.put("received", token.getReceived());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/notifications/connection")
    public ResponseEntity<Map<String, Object>> connect(HttpServletRequest request) {
        String username = username();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        String token = service.createToken(username);
        String base = request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("url", base + "/api/notifications/ingest");
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/notifications/connection")
    public ResponseEntity<Void> disconnect() {
        String username = username();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        service.revoke(username);
        return ResponseEntity.noContent().build();
    }

    public record IngestRequest(String packageName, String appName, String title, String text, Long postedAt) {
    }

    @PostMapping("/notifications/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody IngestRequest body) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        try {
            NotificationService.Outcome outcome = service.ingest(
                    token, body.packageName(), body.appName(), body.title(), body.text(), body.postedAt());
            return ResponseEntity.ok(Map.of("result", outcome.name()));
        } catch (SecurityException exception) {
            return ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(429).body(Map.of("error", exception.getMessage()));
        }
    }

    private static Map<String, Object> bankView(Bank bank) {
        return Map.of("name", bank.name(), "wallet", bank.wallet());
    }

    private static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {
            return null;
        }
        return authentication.getName();
    }
}
