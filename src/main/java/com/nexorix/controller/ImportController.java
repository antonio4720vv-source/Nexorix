package com.nexorix.controller;

import com.nexorix.importer.ImportAudit;
import com.nexorix.importer.ImportAuditEvent;
import com.nexorix.importer.ImportException;
import com.nexorix.importer.ImportPreview;
import com.nexorix.importer.ImportQueueService;
import com.nexorix.importer.ImportService;
import com.nexorix.importer.ImportStatus;
import com.nexorix.report.CsvReportWriter;
import com.nexorix.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
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
 *   GET  /api/imports/audit        historial de auditoria (Contador): que paso con cada archivo
 *   GET  /api/imports/audit.csv    el mismo historial para Excel
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportQueueService queueService;
    private final ImportAudit audit;
    private final UserRepository userRepository;

    public ImportController(ImportService importService, ImportQueueService queueService,
                            ImportAudit audit, UserRepository userRepository) {
        this.importService = importService;
        this.queueService = queueService;
        this.audit = audit;
        this.userRepository = userRepository;
    }

    /** Una linea del historial, sin datos internos. */
    public record AuditLine(Long id, Long batchId, String fileName, String fileHash, String event,
                            String detail, String at) {
        static AuditLine of(ImportAuditEvent event) {
            return new AuditLine(event.getId(), event.getBatchId(), event.getFileName(), event.getFileHash(),
                    event.getEvent(), event.getDetail(), event.getCreatedAt().toString());
        }
    }

    /** Lo mas reciente primero. Con ?archivo=ID, solo la historia de ese archivo (en orden). */
    @GetMapping("/audit")
    public List<AuditLine> auditLog(
            @RequestParam(value = "archivo", required = false) Long batchId,
            @RequestParam(value = "limit", defaultValue = "200") int limit
    ) {
        Long userId = userId();
        List<ImportAuditEvent> events = batchId != null
                ? audit.ofBatch(userId, batchId)
                : audit.recent(userId, limit);
        return events.stream().map(AuditLine::of).toList();
    }

    @GetMapping("/audit.csv")
    public ResponseEntity<byte[]> auditCsv() {
        DateTimeFormatter when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder csv = new StringBuilder("\uFEFFFecha;Archivo;Huella SHA-256;Evento;Detalle\r\n");
        for (ImportAuditEvent event : audit.recent(userId(), ImportAudit.MAX_LIST)) {
            csv.append(String.join(";",
                    CsvReportWriter.cell(event.getCreatedAt().format(when)),
                    CsvReportWriter.cell(event.getFileName()),
                    CsvReportWriter.cell(event.getFileHash()),
                    CsvReportWriter.cell(event.getEvent()),
                    CsvReportWriter.cell(event.getDetail()))).append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexorix-contador-auditoria.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
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

    private Long userId() {
        return userRepository.findByUsername(username())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."))
                .getId();
    }

    /** SecurityConfig ya exige sesion en /api/**. */
    private String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
