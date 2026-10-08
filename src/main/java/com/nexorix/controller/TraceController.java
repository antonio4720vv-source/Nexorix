package com.nexorix.controller;

import com.nexorix.dto.ReconciliationMatchResponse;
import com.nexorix.dto.TraceMatchResponse;
import com.nexorix.trace.ReconciliationMatch;
import com.nexorix.trace.TraceGroupView;
import com.nexorix.trace.TraceMovementView;
import com.nexorix.trace.TraceService;
import com.nexorix.trace.TraceSuggestion;
import com.nexorix.trace.TransferMatch;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace (transferencias entre cuentas propias).
 *
 *   GET  /api/trace/suggestions        sugerencias 1->1, 1->N, N->1 (con comisiones)
 *   POST /api/trace/groups             confirmar  {originIds:[..], destinationIds:[..]}
 *   POST /api/trace/groups/{id}/undo   deshacer
 *   GET  /api/trace/history            historial completo (activas y deshechas)
 *   GET  /api/trace/overview           conteos por estado
 *
 * Se mantienen las rutas anteriores (own-transfers, confirm, matches).
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    public record ConfirmGroupRequest(List<Long> originIds, List<Long> destinationIds) {
    }

    public record SuggestionResponse(
            String key,
            String kind,
            String kindLabel,
            BigDecimal amount,
            BigDecimal fee,
            int score,
            String classification,
            List<TraceMovementView> origins,
            List<TraceMovementView> destinations
    ) {
        static SuggestionResponse of(TraceSuggestion s) {
            return new SuggestionResponse(s.key(), s.kind().name(), s.kind().label(), s.amount(), s.fee(),
                    s.score(), s.classification(),
                    s.origins().stream().map(TraceMovementView::of).toList(),
                    s.destinations().stream().map(TraceMovementView::of).toList());
        }
    }

    // ------------------------------------------------------------
    // Trace V2
    // ------------------------------------------------------------

    @GetMapping("/suggestions")
    public List<SuggestionResponse> suggestions() {
        return traceService.suggestions(username()).stream().map(SuggestionResponse::of).toList();
    }

    @PostMapping("/groups")
    public TraceGroupView confirmGroup(@RequestBody ConfirmGroupRequest body) {
        List<ReconciliationMatch> rows = traceService.confirm(username(), body.originIds(), body.destinationIds());
        return traceService.history(username()).stream()
                .filter(g -> g.groupId().equals(rows.get(0).effectiveGroupId()))
                .findFirst()
                .orElseThrow();
    }

    @PostMapping("/groups/{groupId}/undo")
    public Map<String, Object> undo(@PathVariable String groupId) {
        return Map.of("undone", traceService.undo(username(), groupId));
    }

    @GetMapping("/history")
    public List<TraceGroupView> history() {
        return traceService.history(username());
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        String username = username();
        List<TraceSuggestion> suggestions = traceService.suggestions(username);
        List<TraceGroupView> history = traceService.history(username);

        Map<String, Long> pendingByClassification = new LinkedHashMap<>();
        for (TraceSuggestion s : suggestions) {
            pendingByClassification.merge(s.classification(), 1L, Long::sum);
        }
        BigDecimal internal = history.stream().filter(TraceGroupView::active)
                .map(TraceGroupView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fees = history.stream().filter(TraceGroupView::active)
                .map(TraceGroupView::fee).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", suggestions.size());
        result.put("pendingByClassification", pendingByClassification);
        result.put("confirmed", history.stream().filter(TraceGroupView::active).count());
        result.put("undone", history.stream().filter(g -> !g.active()).count());
        result.put("internalAmount", internal);
        result.put("fees", fees);
        return result;
    }

    // ------------------------------------------------------------
    // Rutas anteriores (compatibilidad)
    // ------------------------------------------------------------

    @GetMapping("/own-transfers")
    public List<TraceMatchResponse> findOwnTransfers() {
        return traceService.findOwnTransfers(username()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/confirm")
    public ReconciliationMatchResponse confirmMatch(@RequestParam Long originTransactionId,
                                                    @RequestParam Long destinationTransactionId) {
        return ReconciliationMatchResponse.fromMatch(
                traceService.confirmMatch(originTransactionId, destinationTransactionId, username()));
    }

    @GetMapping("/matches")
    public List<ReconciliationMatchResponse> getMyMatches() {
        return traceService.getMyReconciliationMatches(username()).stream()
                .filter(ReconciliationMatch::isActive)
                .map(ReconciliationMatchResponse::fromMatch)
                .toList();
    }

    private TraceMatchResponse toResponse(TransferMatch match) {
        var origin = match.getOriginTransaction();
        var destination = match.getDestinationTransaction();
        return new TraceMatchResponse(
                origin.getId(), origin.getAccount().getBank(), origin.getAccount().getName(),
                origin.getAmount(), origin.getTransactionDate(),
                destination.getId(), destination.getAccount().getBank(), destination.getAccount().getName(),
                destination.getAmount(), destination.getTransactionDate(),
                match.getAmount(), match.getScore(), match.getClassification());
    }

    /** SecurityConfig ya exige sesion en /api/**. */
    private static String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
