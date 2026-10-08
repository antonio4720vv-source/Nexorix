package com.nexorix.controller;

import com.nexorix.ai.RateLimiter;
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
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final RateLimiter limiter = new RateLimiter(30, Duration.ofMinutes(10));

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        String username = username();
        checkLimit(username);
        byte[] file = PdfReportWriter.write(reportService.build(username, desde, hasta));
        return download(file, MediaType.APPLICATION_PDF, "nexorix-reporte" + suffix(desde, hasta) + ".pdf");
    }

    @GetMapping("/movimientos.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
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
