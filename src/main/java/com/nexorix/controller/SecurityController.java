package com.nexorix.controller;

import com.nexorix.fraud.SecurityProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Antifraude visto por la persona: preferencias, perfil de comportamiento y avisos dentro de la app. */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityProfileService service;

    public SecurityController(SecurityProfileService service) {
        this.service = service;
    }

    public record PreferencesRequest(Boolean whatsappNotificationsEnabled, String securityPhone) {
    }

    @GetMapping("/preferences")
    public SecurityProfileService.Preferences preferences() {
        return service.preferences(BankController.username());
    }

    @PutMapping("/preferences")
    public SecurityProfileService.Preferences save(@RequestBody PreferencesRequest request) {
        return service.save(BankController.username(), request.whatsappNotificationsEnabled(), request.securityPhone());
    }

    @GetMapping("/profile")
    public SecurityProfileService.Profile profile() {
        return service.profile(BankController.username());
    }

    @GetMapping("/notifications")
    public Map<String, Object> notifications() {
        String username = BankController.username();
        List<SecurityProfileService.NotificationView> items = service.notifications(username);
        return Map.of("unread", service.unread(username), "items", items);
    }

    @PostMapping("/notifications/{id}/read")
    public Map<String, Object> read(@PathVariable Long id) {
        service.markRead(BankController.username(), id);
        return Map.of("ok", true);
    }
}
