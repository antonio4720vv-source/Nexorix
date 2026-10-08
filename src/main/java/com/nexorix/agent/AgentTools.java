package com.nexorix.agent;

import com.nexorix.account.Account;
import com.nexorix.account.AccountService;
import com.nexorix.importer.StatementValues;
import com.nexorix.importer.TransactionClassifier;
import com.nexorix.trace.MoneyFlowService;
import com.nexorix.trace.MoneyFlowSummary;
import com.nexorix.trace.TraceGroupView;
import com.nexorix.trace.TraceMovementView;
import com.nexorix.trace.TraceService;
import com.nexorix.trace.TraceSuggestion;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Las herramientas que el agente puede usar. Todas son de SOLO LECTURA sobre
 * los datos de la persona que pregunta; las acciones (confirmar, descargar)
 * solo se PROPONEN como botones que la persona debe tocar.
 */
@Component
public class AgentTools {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int MAX_LIST = 50;

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final MoneyFlowService moneyFlowService;
    private final TraceService traceService;

    public AgentTools(AccountService accountService, TransactionService transactionService,
                      MoneyFlowService moneyFlowService, TraceService traceService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.moneyFlowService = moneyFlowService;
        this.traceService = traceService;
    }

    // ============================================================
    // DECLARACIONES (lo que el modelo ve)
    // ============================================================

    static List<Map<String, Object>> declarations() {
        Map<String, Object> period = Map.of(
                "desde", prop("string", "Fecha inicial AAAA-MM-DD (opcional)"),
                "hasta", prop("string", "Fecha final AAAA-MM-DD (opcional)"));

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("resumen_financiero",
                "Dinero real de la persona: ingresos y gastos reales (sin transferencias entre sus cuentas), "
                        + "transferencias internas, comisiones, flujo neto, saldo total y sugerencias pendientes.",
                period));
        tools.add(fn("cuentas", "Lista de cuentas con banco, tipo y saldo actual.", Map.of()));

        Map<String, Object> search = new LinkedHashMap<>(period);
        search.put("texto", prop("string", "Palabra a buscar en la descripción (ej: rappi, arriendo)"));
        search.put("tipo", Map.of("type", "string", "enum", List.of("INGRESO", "EGRESO"),
                "description", "INGRESO = entró dinero, EGRESO = salió"));
        search.put("categoria", prop("string", "Código de categoría (ej: MERCADO, TRANSPORTE)"));
        search.put("banco", prop("string", "Nombre del banco o billetera (ej: Nequi)"));
        search.put("monto_min", prop("number", "Valor mínimo en pesos"));
        search.put("monto_max", prop("number", "Valor máximo en pesos"));
        search.put("solo_reales", prop("boolean", "true = excluir transferencias entre cuentas propias"));
        search.put("limite", prop("integer", "Máximo de movimientos a devolver (1-50, por defecto 20)"));
        tools.add(fn("buscar_movimientos",
                "Busca movimientos con filtros. Devuelve la lista (los más recientes primero), el total y la suma.",
                search));

        tools.add(fn("gastos_por_categoria",
                "Gastos e ingresos REALES agrupados por categoría, con porcentajes.", period));
        tools.add(fn("resumen_mensual",
                "Ingresos reales, gastos reales y neto de cada mes.",
                Map.of("meses", prop("integer", "Cuántos meses hacia atrás (1-24, por defecto 6)"))));
        tools.add(fn("gastos_recurrentes",
                "Detecta gastos que se repiten (suscripciones, servicios, pagos frecuentes) y su costo mensual aproximado.",
                Map.of()));
        tools.add(fn("transferencias_sugeridas",
                "Transferencias entre cuentas propias que Trace encontró y la persona no ha confirmado "
                        + "(incluye 1→1, 1→N, N→1 y comisiones). Cada una tiene una 'clave'.",
                Map.of()));
        tools.add(fn("historial_transferencias",
                "Transferencias entre cuentas propias ya confirmadas o deshechas.",
                Map.of("limite", prop("integer", "Máximo a devolver (por defecto 10)"))));
        tools.add(fn("proponer_confirmar_transferencia",
                "Muestra a la persona un botón para confirmar una transferencia sugerida. NO la confirma: "
                        + "la persona decide. Usa la 'clave' de transferencias_sugeridas.",
                Map.of("clave", prop("string", "Clave de la sugerencia, ej: o:12|d:20")), List.of("clave")));
        tools.add(fn("preparar_reporte",
                "Muestra un botón para descargar un reporte conciliado (PDF para el contador o CSV para Excel).",
                Map.of("formato", Map.of("type", "string", "enum", List.of("pdf", "csv")),
                        "desde", prop("string", "Fecha inicial AAAA-MM-DD (opcional)"),
                        "hasta", prop("string", "Fecha final AAAA-MM-DD (opcional)")),
                List.of("formato")));
        return tools;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> fn(String name, String description, Map<String, Object> properties) {
        return fn(name, description, properties, List.of());
    }

    private static Map<String, Object> fn(String name, String description, Map<String, Object> properties,
                                          List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", name);
        declaration.put("description", description);
        // Gemini rechaza un objeto sin propiedades: si no hay parametros, no se envia "parameters".
        if (!properties.isEmpty()) {
            declaration.put("parameters", parameters);
        }
        return declaration;
    }

    // ============================================================
    // EJECUCION
    // ============================================================

    /** Ejecuta una herramienta para la persona autenticada. Nunca lanza: devuelve {"error": ...}. */
    @Transactional(readOnly = true)
    public Object execute(String username, String name, JsonNode args, List<AgentAction> actions) {
        try {
            return switch (name) {
                case "resumen_financiero" -> summary(username, date(args, "desde"), date(args, "hasta"));
                case "cuentas" -> accounts(username);
                case "buscar_movimientos" -> search(username, args);
                case "gastos_por_categoria" -> byCategory(username, date(args, "desde"), date(args, "hasta"));
                case "resumen_mensual" -> monthly(username, Math.max(1, Math.min(24, args.path("meses").asInt(6))));
                case "gastos_recurrentes" -> recurring(username);
                case "transferencias_sugeridas" -> suggestions(username);
                case "historial_transferencias" -> history(username, Math.max(1, Math.min(30, args.path("limite").asInt(10))));
                case "proponer_confirmar_transferencia" -> proposeConfirm(username, args.path("clave").asText(""), actions);
                case "preparar_reporte" -> report(args, actions);
                default -> Map.of("error", "Herramienta desconocida: " + name);
            };
        } catch (IllegalArgumentException exception) {
            return Map.of("error", exception.getMessage());
        } catch (Exception exception) {
            return Map.of("error", "No fue posible consultar los datos.");
        }
    }

    // ------------------------------------------------------------

    private Map<String, Object> summary(String username, LocalDate from, LocalDate to) {
        MoneyFlowSummary s = moneyFlowService.summarize(username, from, to);
        BigDecimal balance = accountService.getAccountsByUsername(username).stream()
                .map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodo", period(from, to));
        result.put("saldo_total_actual", balance);
        result.put("ingresos_segun_movimientos", s.grossIncome());
        result.put("gastos_segun_movimientos", s.grossExpense());
        result.put("transferencias_internas", s.internalTransfers());
        result.put("ingresos_reales", s.realIncome());
        result.put("gastos_reales", s.realExpense());
        result.put("comisiones_de_transferencias", s.transferFees());
        result.put("flujo_neto_real", s.realNetFlow());
        result.put("transferencias_confirmadas", s.confirmedTransfers());
        result.put("sugerencias_pendientes", s.pendingSuggestions());
        result.put("monto_pendiente_por_confirmar", s.pendingAmount());
        return result;
    }

    private List<Map<String, Object>> accounts(String username) {
        return accountService.getAccountsByUsername(username).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", a.getName());
            m.put("banco", a.getBank());
            m.put("tipo", a.getType());
            m.put("saldo", a.getBalance());
            return m;
        }).toList();
    }

    Map<String, Object> search(String username, JsonNode args) {
        LocalDate from = date(args, "desde");
        LocalDate to = date(args, "hasta");
        String text = StatementValues.normalizeForMatching(args.path("texto").asText(""));
        String type = args.path("tipo").asText("").toUpperCase(Locale.ROOT);
        String category = args.path("categoria").asText("").toUpperCase(Locale.ROOT);
        String bank = StatementValues.normalizeForMatching(args.path("banco").asText(""));
        BigDecimal min = args.has("monto_min") ? args.path("monto_min").decimalValue() : null;
        BigDecimal max = args.has("monto_max") ? args.path("monto_max").decimalValue() : null;
        boolean onlyReal = args.path("solo_reales").asBoolean(false);
        int limit = Math.max(1, Math.min(MAX_LIST, args.path("limite").asInt(20)));

        Map<Long, BigDecimal> internal = moneyFlowService.internalPortions(username);
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = 0;
        BigDecimal sum = BigDecimal.ZERO;

        for (Transaction t : transactionService.getRecentTransactionsByUsername(username)) {
            LocalDate day = t.getTransactionDate().toLocalDate();
            if (from != null && day.isBefore(from)) continue;
            if (to != null && day.isAfter(to)) continue;
            if (!type.isEmpty() && !type.equals(t.getType())) continue;
            if (!category.isEmpty() && !category.equals(t.getCategory())) continue;
            if (!text.isEmpty() && !StatementValues.normalizeForMatching(t.getDescription()).contains(text)) continue;
            if (!bank.isEmpty() && !StatementValues.normalizeForMatching(t.getAccount().getBank()).contains(bank)) continue;
            if (min != null && t.getAmount().compareTo(min) < 0) continue;
            if (max != null && t.getAmount().compareTo(max) > 0) continue;
            BigDecimal inside = internal.getOrDefault(t.getId(), BigDecimal.ZERO).min(t.getAmount());
            if (onlyReal && inside.compareTo(t.getAmount()) >= 0) continue;

            total++;
            sum = sum.add(t.getAmount());
            if (rows.size() < limit) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fecha", day.toString());
                m.put("banco", t.getAccount().getBank());
                m.put("tipo", t.getType());
                m.put("valor", t.getAmount());
                m.put("categoria", TransactionClassifier.label(t.getCategory()));
                m.put("descripcion", t.getDescription());
                if (inside.signum() > 0) m.put("transferencia_interna", inside);
                rows.add(m);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_encontrados", total);
        result.put("suma", sum);
        result.put("mostrados", rows.size());
        result.put("movimientos", rows);
        return result;
    }

    Map<String, Object> byCategory(String username, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> internal = moneyFlowService.internalPortions(username);
        Map<String, BigDecimal> expenses = new HashMap<>();
        Map<String, BigDecimal> incomes = new HashMap<>();

        for (Transaction t : transactionService.getRecentTransactionsByUsername(username)) {
            LocalDate day = t.getTransactionDate().toLocalDate();
            if ((from != null && day.isBefore(from)) || (to != null && day.isAfter(to))) continue;
            BigDecimal real = t.getAmount().subtract(internal.getOrDefault(t.getId(), BigDecimal.ZERO).min(t.getAmount()));
            if (real.signum() <= 0) continue;
            boolean income = "INGRESO".equals(t.getType());
            String label = TransactionClassifier.label(t.getCategory() != null ? t.getCategory()
                    : income ? TransactionClassifier.OTHER_INCOME : TransactionClassifier.OTHER_EXPENSE);
            (income ? incomes : expenses).merge(label, real, BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodo", period(from, to));
        result.put("gastos_reales", ranked(expenses));
        result.put("ingresos_reales", ranked(incomes));
        return result;
    }

    private static List<Map<String, Object>> ranked(Map<String, BigDecimal> totals) {
        BigDecimal all = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoria", e.getKey());
                    m.put("total", e.getValue());
                    m.put("porcentaje", all.signum() == 0 ? 0
                            : e.getValue().multiply(BigDecimal.valueOf(100)).divide(all, 1, RoundingMode.HALF_UP));
                    return m;
                }).toList();
    }

    Map<String, Object> monthly(String username, int months) {
        Map<Long, BigDecimal> internal = moneyFlowService.internalPortions(username);
        YearMonth first = YearMonth.now(BOGOTA).minusMonths(months - 1L);
        TreeMap<YearMonth, BigDecimal[]> byMonth = new TreeMap<>();
        for (int i = 0; i < months; i++) {
            byMonth.put(first.plusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Transaction t : transactionService.getRecentTransactionsByUsername(username)) {
            BigDecimal[] totals = byMonth.get(YearMonth.from(t.getTransactionDate()));
            if (totals == null) continue;
            BigDecimal real = t.getAmount().subtract(internal.getOrDefault(t.getId(), BigDecimal.ZERO).min(t.getAmount()));
            if ("INGRESO".equals(t.getType())) totals[0] = totals[0].add(real);
            else totals[1] = totals[1].add(real);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byMonth.forEach((month, totals) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mes", month.toString());
            m.put("ingresos_reales", totals[0]);
            m.put("gastos_reales", totals[1]);
            m.put("neto", totals[0].subtract(totals[1]));
            rows.add(m);
        });
        return Map.of("meses", rows);
    }

    Map<String, Object> recurring(String username) {
        Map<Long, BigDecimal> internal = moneyFlowService.internalPortions(username);
        Map<String, List<Transaction>> groups = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Transaction t : transactionService.getRecentTransactionsByUsername(username)) {
            if (!"EGRESO".equals(t.getType()) || internal.containsKey(t.getId())) continue;
            String key = StatementValues.normalizeForMatching(t.getDescription())
                    .replaceAll("\\d+", "").replaceAll("\\s+", " ").trim();
            if (key.length() < 3) continue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            names.putIfAbsent(key, t.getDescription());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : groups.entrySet()) {
            List<Transaction> list = e.getValue();
            Set<YearMonth> months = new HashSet<>();
            list.forEach(t -> months.add(YearMonth.from(t.getTransactionDate())));
            if (list.size() < 3 && months.size() < 2) continue;
            BigDecimal total = list.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("concepto", names.get(e.getKey()));
            m.put("veces", list.size());
            m.put("meses_distintos", months.size());
            m.put("total", total);
            m.put("promedio_por_vez", total.divide(BigDecimal.valueOf(list.size()), 0, RoundingMode.HALF_UP));
            m.put("costo_mensual_aprox", total.divide(BigDecimal.valueOf(Math.max(1, months.size())), 0, RoundingMode.HALF_UP));
            m.put("categoria", TransactionClassifier.label(list.get(0).getCategory()));
            rows.add(m);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());
        return Map.of("recurrentes", rows.stream().limit(15).toList());
    }

    private Map<String, Object> suggestions(String username) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TraceSuggestion s : traceService.suggestions(username)) {
            if (rows.size() >= 15) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", s.key());
            m.put("tipo", s.kind().label());
            m.put("monto", s.amount());
            m.put("comision", s.fee());
            m.put("puntaje", s.score());
            m.put("clasificacion", s.classification());
            m.put("salidas", s.origins().stream().map(t -> movement(TraceMovementView.of(t))).toList());
            m.put("entradas", s.destinations().stream().map(t -> movement(TraceMovementView.of(t))).toList());
            rows.add(m);
        }
        return Map.of("total", rows.size(), "sugerencias", rows);
    }

    private Map<String, Object> history(String username, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TraceGroupView g : traceService.history(username)) {
            if (rows.size() >= limit) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", g.kindLabel());
            m.put("estado", g.active() ? "CONFIRMADA" : "DESHECHA");
            m.put("monto", g.amount());
            m.put("comision", g.fee());
            m.put("fecha_confirmacion", g.createdAt().toLocalDate().toString());
            m.put("salidas", g.origins().stream().map(this::movement).toList());
            m.put("entradas", g.destinations().stream().map(this::movement).toList());
            rows.add(m);
        }
        return Map.of("transferencias", rows);
    }

    private Map<String, Object> movement(TraceMovementView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("banco", v.bank());
        m.put("valor", v.amount());
        m.put("fecha", v.date().toLocalDate().toString());
        return m;
    }

    private Map<String, Object> proposeConfirm(String username, String key, List<AgentAction> actions) {
        TraceSuggestion s = traceService.suggestions(username).stream()
                .filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe una sugerencia con esa clave. "
                        + "Consulta transferencias_sugeridas primero."));
        String from = String.join(" + ", s.origins().stream().map(t -> t.getAccount().getBank()).toList());
        String to = String.join(" + ", s.destinations().stream().map(t -> t.getAccount().getBank()).toList());
        actions.add(AgentAction.confirmTrace("Confirmar " + from + " → " + to, s.key(), s.originIds(), s.destinationIds()));
        return Map.of("ok", true, "mensaje", "Se mostró un botón para que la persona confirme. Aún NO está confirmada.");
    }

    private Map<String, Object> report(JsonNode args, List<AgentAction> actions) {
        String format = "csv".equalsIgnoreCase(args.path("formato").asText()) ? "csv" : "pdf";
        LocalDate from = date(args, "desde");
        LocalDate to = date(args, "hasta");
        StringBuilder url = new StringBuilder("/api/reports/" + (format.equals("pdf") ? "resumen.pdf" : "movimientos.csv"));
        String sep = "?";
        if (from != null) { url.append(sep).append("desde=").append(from); sep = "&"; }
        if (to != null) url.append(sep).append("hasta=").append(to);
        actions.add(AgentAction.download(format.equals("pdf") ? "Descargar reporte PDF" : "Descargar CSV para Excel",
                url.toString()));
        return Map.of("ok", true, "mensaje", "Se mostró el botón de descarga del reporte " + format.toUpperCase());
    }

    // ------------------------------------------------------------

    static LocalDate date(JsonNode args, String field) {
        String value = args == null ? "" : args.path(field).asText("").trim();
        if (value.isEmpty()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Fecha inválida en '" + field + "': usa AAAA-MM-DD.");
        }
    }

    private static String period(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "todo el historial";
        return (from == null ? "inicio" : from.toString()) + " a " + (to == null ? "hoy" : to.toString());
    }
}
