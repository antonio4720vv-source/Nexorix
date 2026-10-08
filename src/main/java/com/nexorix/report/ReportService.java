package com.nexorix.report;

import com.nexorix.importer.TransactionClassifier;
import com.nexorix.trace.MoneyFlowService;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Arma los datos de los reportes conciliados (para el contador o la declaracion de renta). */
@Service
public class ReportService {

    /** Para no generar archivos gigantes. */
    public static final int MAX_ROWS = 20_000;

    private final TransactionService transactionService;
    private final MoneyFlowService moneyFlowService;
    private final UserRepository userRepository;

    public ReportService(TransactionService transactionService, MoneyFlowService moneyFlowService,
                         UserRepository userRepository) {
        this.transactionService = transactionService;
        this.moneyFlowService = moneyFlowService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ReportData build(String username, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial es posterior a la final.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        Map<Long, BigDecimal> internal = moneyFlowService.internalPortions(username);
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        Map<String, BigDecimal> incomes = new LinkedHashMap<>();
        TreeMap<YearMonth, BigDecimal[]> months = new TreeMap<>();
        List<ReportData.Row> rows = new ArrayList<>();

        List<Transaction> transactions = new ArrayList<>(transactionService.getRecentTransactionsByUsername(username));
        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        for (Transaction t : transactions) {
            LocalDate day = t.getTransactionDate().toLocalDate();
            if ((from != null && day.isBefore(from)) || (to != null && day.isAfter(to))) continue;
            if (rows.size() >= MAX_ROWS) {
                throw new IllegalArgumentException("El periodo tiene demasiados movimientos. Elige un rango más corto.");
            }
            boolean income = "INGRESO".equals(t.getType());
            BigDecimal inside = internal.getOrDefault(t.getId(), BigDecimal.ZERO).min(t.getAmount());
            BigDecimal real = t.getAmount().subtract(inside);
            String category = TransactionClassifier.label(t.getCategory() != null ? t.getCategory()
                    : income ? TransactionClassifier.OTHER_INCOME : TransactionClassifier.OTHER_EXPENSE);

            if (real.signum() > 0) {
                (income ? incomes : expenses).merge(category, real, BigDecimal::add);
                BigDecimal[] m = months.computeIfAbsent(YearMonth.from(day), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                if (income) m[0] = m[0].add(real); else m[1] = m[1].add(real);
            }
            rows.add(new ReportData.Row(day, t.getAccount().getBank(), t.getAccount().getName(), t.getType(),
                    t.getDescription(), category, t.getAmount(), inside, real, t.getReference()));
        }

        List<ReportData.MonthRow> monthRows = new ArrayList<>();
        months.forEach((k, v) -> monthRows.add(new ReportData.MonthRow(k.toString(), v[0], v[1])));

        return new ReportData(user.getName(), user.getCedula(), from, to,
                moneyFlowService.summarize(username, from, to),
                sortDesc(expenses), sortDesc(incomes), monthRows, rows);
    }

    private static Map<String, BigDecimal> sortDesc(Map<String, BigDecimal> map) {
        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        map.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
