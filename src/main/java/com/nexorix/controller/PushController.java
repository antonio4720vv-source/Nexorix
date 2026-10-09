package com.nexorix.controller;

import com.nexorix.push.PushService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Notificaciones del dispositivo (Web Push): llave publica, suscribirse, quitar y probar. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService service;

    public PushController(PushService service) {
        this.service = service;
    }

    public record Keys(String p256dh, String auth) {
    }

    public record SubscribeRequest(String endpoint, Keys keys) {
    }

    public record UnsubscribeRequest(String endpoint) {
    }

    @GetMapping("/key")
    public Map<String, Object> key() {
        String username = BankController.username();
        return Map.of("publicKey", service.publicKey(), "devices", service.devices(username));
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestBody SubscribeRequest body) {
        if (body == null || body.keys() == null) {
            throw new IllegalArgumentException("La suscripción no es válida.");
        }
        service.subscribe(BankController.username(), body.endpoint(), body.keys().p256dh(), body.keys().auth());
        return Map.of("ok", true);
    }

    @PostMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestBody UnsubscribeRequest body) {
        if (body != null && body.endpoint() != null) {
            service.unsubscribe(BankController.username(), body.endpoint());
        }
        return Map.of("ok", true);
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        return Map.of("sent", service.sendTest(BankController.username()));
    }
}
