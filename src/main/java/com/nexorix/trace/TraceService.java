package com.nexorix.trace;

import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trace: transferencias entre cuentas propias.
 *
 *  - suggestions(): lo que Trace encontro y falta confirmar (1->1, 1->N, N->1, con comisiones).
 *  - confirm():     la persona confirma; se guarda como un grupo de filas.
 *  - undo():        deshacer una confirmacion (queda en el historial).
 *  - history():     todas las conciliaciones, activas y deshechas.
 */
@Service
public class TraceService {

    private final TransactionRepository transactionRepository;
    private final ReconciliationMatchRepository matchRepository;

    public TraceService(TransactionRepository transactionRepository,
                        ReconciliationMatchRepository matchRepository) {
        this.transactionRepository = transactionRepository;
        this.matchRepository = matchRepository;
    }

    // ============================================================
    // SUGERENCIAS
    // ============================================================

    @Transactional(readOnly = true)
    public List<TraceSuggestion> suggestions(String username) {
        List<Transaction> transactions =
                transactionRepository.findByAccountUserUsernameOrderByTransactionDateDesc(username);
        return TraceMatcher.suggest(transactions, activeTransactionIds(username));
    }

    /** Version anterior de la API: solo sugerencias 1 -> 1. */
    @Transactional(readOnly = true)
    public List<TransferMatch> findOwnTransfers(String username) {
        return suggestions(username).stream()
                .filter(s -> s.kind() == TraceKind.ONE_TO_ONE)
                .map(s -> new TransferMatch(s.origins().get(0), s.destinations().get(0),
                        s.amount(), s.score(), s.classification()))
                .toList();
    }

    /** Busca una sugerencia vigente por su clave ("o:1|d:2"). */
    @Transactional(readOnly = true)
    public TraceSuggestion findSuggestion(String username, String key) {
        return suggestions(username).stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Esa sugerencia ya no está disponible. Recarga la página."));
    }

    // ============================================================
    // CONFIRMAR
    // ============================================================

    @Transactional
    public List<ReconciliationMatch> confirm(String username, List<Long> originIds, List<Long> destinationIds) {

        Set<Long> origins = new LinkedHashSet<>(originIds == null ? List.of() : originIds);
        Set<Long> destinations = new LinkedHashSet<>(destinationIds == null ? List.of() : destinationIds);

        if (origins.isEmpty() || destinations.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una salida y una entrada.");
        }
        if (origins.size() + destinations.size() > TraceMatcher.MAX_GROUP + 1) {
            throw new IllegalArgumentException("Demasiados movimientos en una sola transferencia.");
        }
        Set<Long> all = new HashSet<>(origins);
        all.addAll(destinations);
        if (all.size() != origins.size() + destinations.size()) {
            throw new IllegalArgumentException("Un movimiento no puede ser origen y destino a la vez.");
        }

        // Bloquea los movimientos: dos confirmaciones simultaneas no pueden usar el mismo.
        Map<Long, Transaction> locked = new LinkedHashMap<>();
        for (Transaction t : transactionRepository.lockByIds(all)) {
            locked.put(t.getId(), t);
        }
        if (locked.size() != all.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado.");
        }
        for (Transaction t : locked.values()) {
            if (!t.getAccount().getUser().getUsername().equals(username)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso sobre esos movimientos.");
            }
        }
        if (matchRepository.existsByStatusAndOriginTransactionIdIn(ReconciliationStatus.MATCHED, all)
                || matchRepository.existsByStatusAndDestinationTransactionIdIn(ReconciliationStatus.MATCHED, all)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alguno de estos movimientos ya está conciliado.");
        }

        List<Transaction> originList = origins.stream().map(locked::get).toList();
        List<Transaction> destinationList = destinations.stream().map(locked::get).toList();

        TraceSuggestion group = TraceMatcher.validate(originList, destinationList);
        String groupId = UUID.randomUUID().toString();

        List<ReconciliationMatch> rows = buildRows(group, groupId);
        return matchRepository.saveAll(rows);
    }

    /** Version anterior de la API (1 -> 1). */
    @Transactional
    public ReconciliationMatch confirmMatch(Long originTransactionId, Long destinationTransactionId, String username) {
        return confirm(username, List.of(originTransactionId), List.of(destinationTransactionId)).get(0);
    }

    /**
     * Convierte un grupo en filas.
     *  1 -> 1 y 1 -> N: una fila por destino; lo conciliado es lo que llego.
     *  N -> 1: una fila por origen; si hubo comision se descuenta del origen mas grande.
     */
    static List<ReconciliationMatch> buildRows(TraceSuggestion group, String groupId) {
        List<ReconciliationMatch> rows = new ArrayList<>();
        BigDecimal fee = group.fee();

        if (group.kind() == TraceKind.MANY_TO_ONE) {
            Transaction destination = group.destinations().get(0);
            Transaction largest = group.origins().stream()
                    .max(Comparator.comparing(Transaction::getAmount)).orElseThrow();
            for (Transaction origin : group.origins()) {
                boolean takesFee = origin == largest && fee.signum() > 0;
                BigDecimal matched = takesFee ? origin.getAmount().subtract(fee) : origin.getAmount();
                rows.add(new ReconciliationMatch(origin, destination, matched, takesFee ? fee : BigDecimal.ZERO,
                        group.score(), ReconciliationStatus.MATCHED, group.classification(), groupId, group.kind()));
            }
        } else {
            Transaction origin = group.origins().get(0);
            boolean first = true;
            for (Transaction destination : group.destinations()) {
                rows.add(new ReconciliationMatch(origin, destination, destination.getAmount(),
                        first ? fee : BigDecimal.ZERO, group.score(), ReconciliationStatus.MATCHED,
                        group.classification(), groupId, group.kind()));
                first = false;
            }
        }
        return rows;
    }

    // ============================================================
    // DESHACER
    // ============================================================

    @Transactional
    public int undo(String username, String groupId) {
        List<ReconciliationMatch> rows = rowsOfGroup(username, groupId);
        int changed = 0;
        for (ReconciliationMatch row : rows) {
            if (row.isActive()) {
                row.undo();
                changed++;
            }
        }
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa transferencia ya estaba deshecha.");
        }
        matchRepository.saveAll(rows);
        return changed;
    }

    private List<ReconciliationMatch> rowsOfGroup(String username, String groupId) {
        List<ReconciliationMatch> rows;
        if (groupId != null && groupId.startsWith("m") && groupId.substring(1).matches("\\d+")) {
            rows = matchRepository.findById(Long.parseLong(groupId.substring(1))).map(List::of).orElse(List.of());
        } else {
            rows = matchRepository.findByGroupId(groupId);
        }
        if (rows.isEmpty() || rows.stream().anyMatch(r ->
                !r.getOriginTransaction().getAccount().getUser().getUsername().equals(username))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transferencia no encontrada.");
        }
        return rows;
    }

    // ============================================================
    // HISTORIAL
    // ============================================================

    @Transactional(readOnly = true)
    public List<ReconciliationMatch> getMyReconciliationMatches(String username) {
        return matchRepository.findByOriginTransactionAccountUserUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<TraceGroupView> history(String username) {
        Map<String, List<ReconciliationMatch>> groups = new LinkedHashMap<>();
        for (ReconciliationMatch row : getMyReconciliationMatches(username)) {
            groups.computeIfAbsent(row.effectiveGroupId(), k -> new ArrayList<>()).add(row);
        }
        List<TraceGroupView> result = new ArrayList<>();
        for (Map.Entry<String, List<ReconciliationMatch>> entry : groups.entrySet()) {
            result.add(toView(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    static TraceGroupView toView(String groupId, List<ReconciliationMatch> rows) {
        ReconciliationMatch first = rows.get(0);
        Map<Long, Transaction> origins = new LinkedHashMap<>();
        Map<Long, Transaction> destinations = new LinkedHashMap<>();
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        for (ReconciliationMatch row : rows) {
            origins.putIfAbsent(row.getOriginTransaction().getId(), row.getOriginTransaction());
            destinations.putIfAbsent(row.getDestinationTransaction().getId(), row.getDestinationTransaction());
            amount = amount.add(row.getMatchedAmount());
            fee = fee.add(row.effectiveFee());
        }
        TraceKind kind = first.effectiveKind();
        return new TraceGroupView(groupId, kind.name(), kind.label(), first.isActive(), first.getScore(),
                first.getClassification(), amount, fee, first.getCreatedAt(), first.getUndoneAt(),
                origins.values().stream().map(TraceMovementView::of).toList(),
                destinations.values().stream().map(TraceMovementView::of).toList());
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    /** Movimientos que ya estan en una conciliacion activa. */
    @Transactional(readOnly = true)
    public Set<Long> activeTransactionIds(String username) {
        Set<Long> ids = new HashSet<>();
        for (ReconciliationMatch row : getMyReconciliationMatches(username)) {
            if (row.isActive()) {
                ids.add(row.getOriginTransaction().getId());
                ids.add(row.getDestinationTransaction().getId());
            }
        }
        return ids;
    }
}
