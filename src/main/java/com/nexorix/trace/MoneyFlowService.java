package com.nexorix.trace;

import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calcula el flujo real de dinero usando las conciliaciones de Trace.
 *
 *   Nequi      EGRESO  $1.000.000  ─┐  conciliado por Trace
 *   Davivienda INGRESO $  995.000  ─┘  (llegaron $995.000: $5.000 de comision)
 *   Empresa    INGRESO $3.000.000      salario: dinero nuevo
 *
 *   Ingresos reales: $3.000.000   Gastos reales: $5.000 (la comision)
 *   Transferencias internas: $995.000
 *
 * Cada movimiento tiene una "parte interna" (lo conciliado). Lo que sobra
 * es dinero real. Asi funcionan igual las transferencias 1 -> 1, 1 -> N y N -> 1.
 */
@Service
public class MoneyFlowService {

    private final TransactionRepository transactionRepository;
    private final ReconciliationMatchRepository matchRepository;
    private final TraceService traceService;

    public MoneyFlowService(TransactionRepository transactionRepository,
                            ReconciliationMatchRepository matchRepository,
                            TraceService traceService) {
        this.transactionRepository = transactionRepository;
        this.matchRepository = matchRepository;
        this.traceService = traceService;
    }

    @Transactional(readOnly = true)
    public MoneyFlowSummary summarize(String username) {
        return summarize(username, null, null);
    }

    /** Resumen de un periodo (fechas incluidas; null = sin limite). */
    @Transactional(readOnly = true)
    public MoneyFlowSummary summarize(String username, LocalDate from, LocalDate to) {

        List<Transaction> transactions = transactionRepository
                .findByAccountUserUsernameOrderByTransactionDateDesc(username).stream()
                .filter(t -> inPeriod(t, from, to))
                .toList();

        List<ReconciliationMatch> active = matchRepository
                .findByOriginTransactionAccountUserUsernameOrderByCreatedAtDesc(username).stream()
                .filter(ReconciliationMatch::isActive)
                .toList();

        Map<Long, BigDecimal> internal = internalPortions(active);

        BigDecimal grossIncome = BigDecimal.ZERO, grossExpense = BigDecimal.ZERO;
        BigDecimal realIncome = BigDecimal.ZERO, realExpense = BigDecimal.ZERO;
        BigDecimal internalTransfers = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            BigDecimal amount = t.getAmount();
            BigDecimal inside = internal.getOrDefault(t.getId(), BigDecimal.ZERO).min(amount);
            BigDecimal real = amount.subtract(inside);
            if ("INGRESO".equalsIgnoreCase(t.getType())) {
                grossIncome = grossIncome.add(amount);
                realIncome = realIncome.add(real);
                internalTransfers = internalTransfers.add(inside);
            } else if ("EGRESO".equalsIgnoreCase(t.getType())) {
                grossExpense = grossExpense.add(amount);
                realExpense = realExpense.add(real);
            }
        }

        Set<String> groups = new HashSet<>();
        BigDecimal fees = BigDecimal.ZERO;
        for (ReconciliationMatch row : active) {
            if (inPeriod(row.getOriginTransaction(), from, to)) {
                groups.add(row.effectiveGroupId());
                fees = fees.add(row.effectiveFee());
            }
        }

        // Sugerencias pendientes: cada movimiento de salida se cuenta una sola vez.
        Set<Long> counted = new HashSet<>();
        int pending = 0;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        for (TraceSuggestion s : traceService.suggestions(username)) {
            if (s.origins().stream().noneMatch(o -> inPeriod(o, from, to))) continue;
            if (s.originIds().stream().anyMatch(counted::contains)) continue;
            counted.addAll(s.originIds());
            pending++;
            pendingAmount = pendingAmount.add(s.amount());
        }

        return new MoneyFlowSummary(grossIncome, grossExpense, internalTransfers, realIncome, realExpense,
                realIncome.subtract(realExpense), groups.size(), pending, pendingAmount, fees);
    }

    /** Parte de cada movimiento que fue transferencia interna (suma de lo conciliado). */
    public static Map<Long, BigDecimal> internalPortions(List<ReconciliationMatch> activeRows) {
        Map<Long, BigDecimal> internal = new HashMap<>();
        for (ReconciliationMatch row : activeRows) {
            internal.merge(row.getOriginTransaction().getId(), row.getMatchedAmount(), BigDecimal::add);
            internal.merge(row.getDestinationTransaction().getId(), row.getMatchedAmount(), BigDecimal::add);
        }
        return internal;
    }

    /** Ids de movimientos que son 100 % transferencia interna. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> internalPortions(String username) {
        return internalPortions(matchRepository
                .findByOriginTransactionAccountUserUsernameOrderByCreatedAtDesc(username).stream()
                .filter(ReconciliationMatch::isActive).toList());
    }

    static boolean inPeriod(Transaction t, LocalDate from, LocalDate to) {
        if (t.getTransactionDate() == null) return from == null && to == null;
        LocalDate day = t.getTransactionDate().toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }
}
