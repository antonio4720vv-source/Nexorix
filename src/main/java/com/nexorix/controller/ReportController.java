package com.nexorix.controller;

import com.nexorix.ai.RateLimiter;
import com.nexorix.auth.AccountLockedException;
import com.nexorix.auth.AuthException;
import com.nexorix.auth.AuthService;
import com.nexorix.dto.PinRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import com.nexorix.report.CsvReportWriter;
import com.nexorix.report.PdfReportWriter;
import com.nexorix.report.ReportData;
import com.nexorix.report.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Reportes conciliados:
 *   GET /api/reports/resumen.pdf?desde=AAAA-MM-DD&hasta=AAAA-MM-DD
 *   GET /api/reports/movimientos.csv?desde=...&hasta=...
 *   GET /api/reports/preview?desde=...&hasta=...   (los numeros, para la pagina)
 *   POST /api/reports/pin  {pin}                   (desbloquea las descargas por 3 minutos)
 *
 * Descargar un extracto exige el PIN de la cuenta: aunque alguien tenga la sesion abierta,
 * no puede sacar los movimientos sin conocerlo.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final RateLimiter limiter = new RateLimiter(30, Duration.ofMinutes(10));
    private final AuthService authService;

    static final String UNLOCKED_UNTIL = "reports.unlockedUntil";
    static final Duration UNLOCK_WINDOW = Duration.ofMinutes(3);

    public ReportController(ReportService reportService, AuthService authService) {
        this.reportService = reportService;
        this.authService = authService;
    }

    /** Revisa el PIN; si es correcto, las descargas quedan habilitadas 3 minutos en esta sesion. */
    @PostMapping("/pin")
    public Map<String, Object> unlock(@RequestBody PinRequest body, HttpServletRequest request) {
        String username = username();
        try {
            authService.verifyPinOf(username, body == null ? null : body.pin());
        } catch (AccountLockedException exception) {
            throw new ResponseStatusException(HttpStatus.LOCKED, exception.getMessage());
        } catch (AuthException exception) {
            // 403 y no 401: la pagina entiende 401 como "sesion vencida" y te sacaria al login.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage());
        }
        request.getSession(true).setAttribute(UNLOCKED_UNTIL, Instant.now().plus(UNLOCK_WINDOW));
        return Map.of("ok", true, "seconds", UNLOCK_WINDOW.toSeconds());
    }

    private static void requireUnlocked(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object until = session == null ? null : session.getAttribute(UNLOCKED_UNTIL);
        if (!(until instanceof Instant limit) || limit.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Escribe tu PIN para descargar el extracto.");
        }
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ReportData data = reportService.build(username(), desde, hasta);
        return Map.of(
                "summary", data.summary(),
                "incomes", data.realIncomesByCategory(),
                "expenses", data.realExpensesByCategory(),
                "months", data.months(),
                "movements", data.rows().size());
    }

    @GetMapping("/resumen.pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            HttpServletRequest request) {
        requireUnlocked(request);
        String username = username();
        checkLimit(username);
        byte[] file = PdfReportWriter.write(reportService.build(username, desde, hasta));
        return download(file, MediaType.APPLICATION_PDF, "nexorix-reporte" + suffix(desde, hasta) + ".pdf");
    }

    @GetMapping("/movimientos.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            HttpServletRequest request) {
        requireUnlocked(request);
        String username = username();
        checkLimit(username);
        byte[] file = CsvReportWriter.write(reportService.build(username, desde, hasta));
        return download(file, new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8),
                "nexorix-movimientos" + suffix(desde, hasta) + ".csv");
    }

    private void checkLimit(String username) {
        if (!limiter.tryAcquire(username)) {
            throw new IllegalArgumentException("Generaste muchos reportes seguidos. Espera unos minutos.");
        }
    }

    private static ResponseEntity<byte[]> download(byte[] file, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .cacheControl(CacheControl.noStore()) // datos financieros: que no queden en caches
                .body(file);
    }

    private static String suffix(LocalDate from, LocalDate to) {
        return (from == null ? "" : "-" + from) + (to == null ? "" : "-a-" + to);
    }

    private static String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
