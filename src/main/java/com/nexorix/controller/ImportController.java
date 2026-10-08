package com.nexorix.controller;

import com.nexorix.importer.ImportException;
import com.nexorix.importer.ImportPreview;
import com.nexorix.importer.ImportQueueService;
import com.nexorix.importer.ImportService;
import com.nexorix.importer.ImportStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Importar extractos (PDF o CSV), varios a la vez:
 *   POST /api/imports              sube 1 a 10 archivos -> quedan en cola
 *   GET  /api/imports?ids=1,2,3    estado de cada archivo
 *   GET  /api/imports/{id}         vista previa (filas)
 *   POST /api/imports/{id}/confirm importar las filas elegidas
 *   POST /api/imports/{id}/cancel  descartar
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportQueueService queueService;

    public ImportController(ImportService importService, ImportQueueService queueService) {
        this.importService = importService;
        this.queueService = queueService;
    }

    public record ConfirmRequest(List<Long> rowIds) {
    }

    @PostMapping
    public List<ImportStatus> upload(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile single,
            @RequestParam Long accountId,
            @RequestParam(required = false) String password
    ) throws IOException {

        List<MultipartFile> all = new ArrayList<>();
        if (files != null) all.addAll(files);
        if (single != null) all.add(single);

        List<ImportQueueService.Incoming> incoming = new ArrayList<>();
        for (MultipartFile file : all) {
            if (!file.isEmpty()) {
                incoming.add(new ImportQueueService.Incoming(file.getOriginalFilename(), file.getBytes()));
            }
        }

        return queueService.enqueue(username(), accountId, incoming, password);
    }

    @GetMapping
    public List<ImportStatus> status(@RequestParam List<Long> ids) {
        return queueService.status(username(), ids);
    }

    @GetMapping("/{id}")
    public ImportPreview get(@PathVariable Long id) {
        return importService.get(username(), id);
    }

    @PostMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, @RequestBody ConfirmRequest body) {
        int imported = importService.confirm(username(), id, body.rowIds());
        return Map.of("imported", imported);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        importService.cancel(username(), id);
        return Map.of("ok", true);
    }

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<Map<String, Object>> handle(ImportException exception) {
        int status = ImportException.NOT_FOUND.equals(exception.getCode()) ? 404 : 400;
        return ResponseEntity.status(status).body(Map.of(
                "error", exception.getMessage(),
                "code", exception.getCode()
        ));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> concurrent() {
        return ResponseEntity.status(409).body(Map.of(
                "error", "Esta importación se está confirmando en otra ventana. Recarga la página."));
    }

    /** SecurityConfig ya exige sesion en /api/**. */
    private String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
