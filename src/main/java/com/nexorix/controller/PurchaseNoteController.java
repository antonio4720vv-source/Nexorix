package com.nexorix.controller;

import com.nexorix.whatsapp.PurchaseNoteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Tabla personalizada de compras y conexion con WhatsApp (pagina /compras.html). */
@RestController
@RequestMapping("/api/compras")
public class PurchaseNoteController {

    private final PurchaseNoteService service;

    public PurchaseNoteController(PurchaseNoteService service) {
        this.service = service;
    }

    public record PhoneRequest(String phone) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record ColumnRequest(String label, String hint, Integer position) {
    }

    // ---------------- WhatsApp ----------------

    @GetMapping("/whatsapp")
    public PurchaseNoteService.Settings settings() {
        return service.settings(username());
    }

    @PutMapping("/whatsapp")
    public PurchaseNoteService.Settings savePhone(@RequestBody PhoneRequest body) {
        return service.savePhone(username(), body == null ? null : body.phone());
    }

    @PutMapping("/whatsapp/activo")
    public PurchaseNoteService.Settings setEnabled(@RequestBody EnabledRequest body) {
        return service.setEnabled(username(), body != null && body.enabled());
    }

    @DeleteMapping("/whatsapp")
    public Map<String, Object> removePhone() {
        service.removePhone(username());
        return Map.of("ok", true);
    }

    // ---------------- Columnas ----------------

    @GetMapping("/columnas")
    public List<PurchaseNoteService.ColumnView> columns() {
        return service.columns(username());
    }

    @PostMapping("/columnas")
    public PurchaseNoteService.ColumnView addColumn(@RequestBody ColumnRequest body) {
        return service.addColumn(username(), body == null ? null : body.label(), body == null ? null : body.hint());
    }

    @PutMapping("/columnas/{id}")
    public PurchaseNoteService.ColumnView updateColumn(@PathVariable Long id, @RequestBody ColumnRequest body) {
        return service.updateColumn(username(), id, body == null ? null : body.label(),
                body == null ? null : body.hint(), body == null ? null : body.position());
    }

    @DeleteMapping("/columnas/{id}")
    public Map<String, Object> deleteColumn(@PathVariable Long id) {
        service.deleteColumn(username(), id);
        return Map.of("ok", true);
    }

    // ---------------- Filas ----------------

    @GetMapping
    public List<PurchaseNoteService.NoteView> notes() {
        return service.notes(username());
    }

    public record CategoryRequest(String category) {
    }

    @GetMapping("/categorias")
    public List<PurchaseNoteService.CategoryView> categories() {
        return service.categories(username());
    }

    @PutMapping("/{id}/categoria")
    public PurchaseNoteService.NoteView updateCategory(@PathVariable Long id, @RequestBody CategoryRequest body) {
        return service.updateCategory(username(), id, body == null ? null : body.category());
    }

    @PutMapping("/{id}")
    public PurchaseNoteService.NoteView updateValues(@PathVariable Long id, @RequestBody Map<String, String> values) {
        return service.updateValues(username(), id, values);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteNote(@PathVariable Long id) {
        service.deleteNote(username(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/compras.csv")
    public ResponseEntity<byte[]> csv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexorix-compras.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(service.csv(username()));
    }

    private static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        return authentication.getName();
    }
}
