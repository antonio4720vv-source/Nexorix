package com.nexorix.trace;

import com.nexorix.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Motor de Trace V2: encuentra transferencias entre cuentas propias.
 *
 * Soporta:
 *  - 1 -> 1: una salida y una entrada.
 *  - 1 -> N: una salida repartida en varias entradas.
 *  - N -> 1: varias salidas que llegan juntas en una entrada.
 *  - Comisiones: la entrada puede ser un poco MENOR que la salida (costo
 *    de transferencia interbancaria). La diferencia se registra como comision.
 *
 * Es logica pura (sin base de datos), para poder probarla facilmente.
 */
public final class TraceMatcher {

    /** Ventana maxima entre la salida y la entrada. */
    public static final Duration WINDOW = Duration.ofHours(48);

    /** Maximo de movimientos del lado "N" en una transferencia 1 -> N o N -> 1. */
    public static final int MAX_GROUP = 4;

    /** Candidatos que se revisan para armar grupos (los mas cercanos en el tiempo). */
    static final int GROUP_CANDIDATES = 12;

    private static final BigDecimal ONE_PESO = BigDecimal.ONE;

    private TraceMatcher() {
    }

    // ============================================================
    // TOLERANCIA Y PUNTAJE
    // ============================================================

    /**
     * Diferencia maxima aceptada como comision para un monto.
     * 1 % del monto, pero al menos lo razonable para una comision interbancaria
     * (hasta $5.000 sin pasar del 10 % del monto) y nunca mas de $20.000.
     */
    public static BigDecimal tolerance(BigDecimal amount) {
        BigDecimal onePercent = amount.multiply(new BigDecimal("0.01"));
        BigDecimal tenPercent = amount.multiply(new BigDecimal("0.10"));
        BigDecimal fixedFee = new BigDecimal("5000").min(tenPercent);
        BigDecimal tolerance = onePercent.max(fixedFee).min(new BigDecimal("20000"));
        return tolerance.max(ONE_PESO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Puntaje 0-100:
     *   mismo usuario +40 (siempre), monto exacto +30 (con comision +15),
     *   tiempo <= 60 min +20 (<= 24 h +10), misma referencia +10,
     *   grupos 1 -> N / N -> 1 -5 (son mas inciertos).
     */
    static int score(TraceKind kind, BigDecimal fee, Duration maxGap, boolean sameReference) {
        int score = 40;
        score += fee.compareTo(ONE_PESO) < 0 ? 30 : 15;
        if (maxGap.compareTo(Duration.ofMinutes(60)) <= 0) {
            score += 20;
        } else if (maxGap.compareTo(Duration.ofHours(24)) <= 0) {
            score += 10;
        }
        if (sameReference) {
            score += 10;
        }
        if (kind != TraceKind.ONE_TO_ONE) {
            score -= 5;
        }
        return Math.max(0, Math.min(100, score));
    }

    public static String classify(int score) {
        if (score >= 90) return "TRANSFERENCIA PROPIA MUY PROBABLE";
        if (score >= 70) return "TRANSFERENCIA PROPIA PROBABLE";
        if (score >= 50) return "REVISAR";
        return "NO CLASIFICADA";
    }

    // ============================================================
    // BUSQUEDA DE SUGERENCIAS
    // ============================================================

    /**
     * @param transactions movimientos de UNA persona
     * @param alreadyMatched ids que ya estan en una conciliacion activa
     */
    public static List<TraceSuggestion> suggest(List<Transaction> transactions, Set<Long> alreadyMatched) {

        List<Transaction> outs = new ArrayList<>();
        List<Transaction> ins = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getId() == null || alreadyMatched.contains(t.getId()) || t.getAmount() == null
                    || t.getAmount().signum() <= 0 || t.getTransactionDate() == null) {
                continue;
            }
            if ("EGRESO".equalsIgnoreCase(t.getType())) outs.add(t);
            else if ("INGRESO".equalsIgnoreCase(t.getType())) ins.add(t);
        }

        Comparator<Transaction> byDate = Comparator.comparing(Transaction::getTransactionDate);
        outs.sort(byDate);
        ins.sort(byDate);

        List<TraceSuggestion> result = new ArrayList<>();
        Set<Long> outsWithExact = new HashSet<>();
        Set<Long> insWithExact = new HashSet<>();

        // ---------- 1 -> 1 ----------
        for (Transaction out : outs) {
            for (Transaction in : window(ins, out.getTransactionDate())) {
                if (sameAccount(out, in) || in.getAmount().compareTo(out.getAmount()) > 0) {
                    continue;
                }
                BigDecimal fee = out.getAmount().subtract(in.getAmount());
                if (fee.compareTo(tolerance(out.getAmount())) > 0) {
                    continue;
                }
                if (fee.compareTo(ONE_PESO) < 0) {
                    fee = BigDecimal.ZERO;
                    outsWithExact.add(out.getId());
                    insWithExact.add(in.getId());
                }
                result.add(build(TraceKind.ONE_TO_ONE, List.of(out), List.of(in), fee));
            }
        }

        // ---------- 1 -> N ----------
        for (Transaction out : outs) {
            if (outsWithExact.contains(out.getId())) continue;
            List<Transaction> candidates = nearest(window(ins, out.getTransactionDate()).stream()
                    .filter(in -> !sameAccount(out, in) && in.getAmount().compareTo(out.getAmount()) < 0
                            && !insWithExact.contains(in.getId()))
                    .toList(), out.getTransactionDate());
            List<Transaction> best = bestSubset(candidates, out.getAmount(), true);
            if (best != null) {
                BigDecimal fee = out.getAmount().subtract(sum(best));
                result.add(build(TraceKind.ONE_TO_MANY, List.of(out), best, fee.compareTo(ONE_PESO) < 0 ? BigDecimal.ZERO : fee));
            }
        }

        // ---------- N -> 1 ----------
        for (Transaction in : ins) {
            if (insWithExact.contains(in.getId())) continue;
            List<Transaction> candidates = nearest(window(outs, in.getTransactionDate()).stream()
                    .filter(out -> !sameAccount(out, in) && out.getAmount().compareTo(in.getAmount()) < 0
                            && !outsWithExact.contains(out.getId()))
                    .toList(), in.getTransactionDate());
            List<Transaction> best = bestSubset(candidates, in.getAmount(), false);
            if (best != null) {
                BigDecimal fee = sum(best).subtract(in.getAmount());
                result.add(build(TraceKind.MANY_TO_ONE, best, List.of(in), fee.compareTo(ONE_PESO) < 0 ? BigDecimal.ZERO : fee));
            }
        }

        result.sort(Comparator.comparingInt(TraceSuggestion::score).reversed()
                .thenComparing(TraceSuggestion::fee));
        return result;
    }

    // ============================================================
    // VALIDAR UNA CONFIRMACION (lo que la persona eligio)
    // ============================================================

    /**
     * Revisa que un grupo elegido por la persona sea una transferencia valida.
     * Lanza IllegalArgumentException con un mensaje claro si no lo es.
     */
    public static TraceSuggestion validate(List<Transaction> origins, List<Transaction> destinations) {

        if (origins.isEmpty() || destinations.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una salida y una entrada.");
        }
        if (origins.size() > 1 && destinations.size() > 1) {
            throw new IllegalArgumentException("Solo se permite 1 → N o N → 1, no N → N.");
        }
        if (origins.size() > MAX_GROUP || destinations.size() > MAX_GROUP) {
            throw new IllegalArgumentException("Máximo " + MAX_GROUP + " movimientos por lado.");
        }
        for (Transaction o : origins) {
            if (!"EGRESO".equalsIgnoreCase(o.getType())) {
                throw new IllegalArgumentException("Los movimientos de origen deben ser salidas de dinero.");
            }
            for (Transaction d : destinations) {
                if (sameAccount(o, d)) {
                    throw new IllegalArgumentException("El origen y el destino deben ser cuentas diferentes.");
                }
                if (gap(o.getTransactionDate(), d.getTransactionDate()).compareTo(WINDOW) > 0) {
                    throw new IllegalArgumentException("Los movimientos están separados por más de 48 horas.");
                }
            }
        }
        for (Transaction d : destinations) {
            if (!"INGRESO".equalsIgnoreCase(d.getType())) {
                throw new IllegalArgumentException("Los movimientos de destino deben ser entradas de dinero.");
            }
        }

        BigDecimal sent = sum(origins);
        BigDecimal received = sum(destinations);
        BigDecimal fee = sent.subtract(received);

        if (fee.compareTo(ONE_PESO.negate()) < 0) {
            throw new IllegalArgumentException("Llegó más dinero del que salió: no parece una transferencia propia.");
        }
        if (fee.compareTo(tolerance(sent)) > 0) {
            throw new IllegalArgumentException("Los montos no cuadran: la diferencia es mayor que una comisión razonable.");
        }
        if (fee.compareTo(ONE_PESO) < 0) {
            fee = BigDecimal.ZERO;
        }

        TraceKind kind = origins.size() > 1 ? TraceKind.MANY_TO_ONE
                : destinations.size() > 1 ? TraceKind.ONE_TO_MANY : TraceKind.ONE_TO_ONE;
        return build(kind, origins, destinations, fee);
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    private static TraceSuggestion build(TraceKind kind, List<Transaction> origins,
                                         List<Transaction> destinations, BigDecimal fee) {
        Duration maxGap = Duration.ZERO;
        for (Transaction o : origins) {
            for (Transaction d : destinations) {
                Duration g = gap(o.getTransactionDate(), d.getTransactionDate());
                if (g.compareTo(maxGap) > 0) maxGap = g;
            }
        }
        int score = score(kind, fee, maxGap, sameReference(origins, destinations));
        return new TraceSuggestion(kind, List.copyOf(origins), List.copyOf(destinations),
                sum(destinations), fee.setScale(2, RoundingMode.HALF_UP), score, classify(score));
    }

    /** Movimientos (ordenados por fecha) dentro de +/- 48 h de la fecha dada. */
    static List<Transaction> window(List<Transaction> sorted, LocalDateTime center) {
        LocalDateTime from = center.minus(WINDOW);
        LocalDateTime to = center.plus(WINDOW);
        int low = 0, high = sorted.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sorted.get(mid).getTransactionDate().isBefore(from)) low = mid + 1;
            else high = mid;
        }
        List<Transaction> result = new ArrayList<>();
        for (int i = low; i < sorted.size() && !sorted.get(i).getTransactionDate().isAfter(to); i++) {
            result.add(sorted.get(i));
        }
        return result;
    }

    private static List<Transaction> nearest(List<Transaction> list, LocalDateTime center) {
        return list.stream()
                .sorted(Comparator.comparing(t -> gap(t.getTransactionDate(), center)))
                .limit(GROUP_CANDIDATES)
                .toList();
    }

    /**
     * Busca el mejor subconjunto (2 a 4 movimientos) cuya suma cuadre con el objetivo.
     * oneToMany: suma <= objetivo (la diferencia es comision de la salida).
     * manyToOne: suma >= objetivo (la diferencia es comision de las salidas).
     */
    static List<Transaction> bestSubset(List<Transaction> candidates, BigDecimal target, boolean oneToMany) {
        List<Transaction> best = null;
        BigDecimal bestDiff = null;
        int n = candidates.size();

        for (int size = 2; size <= Math.min(MAX_GROUP, n); size++) {
            int[] idx = new int[size];
            for (int i = 0; i < size; i++) idx[i] = i;
            while (true) {
                BigDecimal total = BigDecimal.ZERO;
                for (int i : idx) total = total.add(candidates.get(i).getAmount());

                BigDecimal sent = oneToMany ? target : total;
                BigDecimal diff = oneToMany ? target.subtract(total) : total.subtract(target);

                if (diff.signum() >= 0 && diff.compareTo(tolerance(sent)) <= 0
                        && (bestDiff == null || diff.compareTo(bestDiff) < 0)) {
                    bestDiff = diff;
                    best = new ArrayList<>();
                    for (int i : idx) best.add(candidates.get(i));
                }

                // siguiente combinacion
                int k = size - 1;
                while (k >= 0 && idx[k] == n - size + k) k--;
                if (k < 0) break;
                idx[k]++;
                for (int j = k + 1; j < size; j++) idx[j] = idx[j - 1] + 1;
            }
            if (bestDiff != null && bestDiff.compareTo(ONE_PESO) < 0) {
                break; // exacto con pocos movimientos: no hace falta buscar grupos mas grandes
            }
        }
        return best;
    }

    static BigDecimal sum(List<Transaction> list) {
        return list.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static Duration gap(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).abs();
    }

    private static boolean sameAccount(Transaction a, Transaction b) {
        return a.getAccount() != null && b.getAccount() != null
                && a.getAccount().getId() != null
                && a.getAccount().getId().equals(b.getAccount().getId());
    }

    private static boolean sameReference(List<Transaction> origins, List<Transaction> destinations) {
        String reference = null;
        for (Transaction t : origins) {
            if (t.getReference() == null || t.getReference().isBlank()) return false;
            if (reference == null) reference = t.getReference().trim();
            else if (!reference.equalsIgnoreCase(t.getReference().trim())) return false;
        }
        for (Transaction t : destinations) {
            if (t.getReference() == null || !t.getReference().trim().equalsIgnoreCase(reference)) return false;
        }
        return reference != null;
    }
}
