package com.nexorix.fraud;

import com.nexorix.banking.BankMovementKind;
import com.nexorix.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Motor antifraude. Dos reglas, de mayor a menor prioridad:
 *
 *  1. CRITICAL - imposibilidad fisica: la distancia entre la ultima operacion con ubicacion
 *     y la nueva no se puede recorrer en el tiempo transcurrido (ej. Bogota 10:00 -> Miami 11:00).
 *  2. ANOMALY - comercio inusual o destinatario nuevo (solo avisa dentro de la app).
 */
@Component
public class FraudEngine {

    public enum Level { NONE, ANOMALY, CRITICAL }

    /** Lo que se sabe de un movimiento que va a evaluarse. */
    public record Observation(BankMovementKind kind, String merchant, String category, String recipientName,
                              String recipientRef, BigDecimal amount, Geo geo, String ip, LocalDateTime at) {
    }

    public record Verdict(Level level, String reason, Geo previous, double distanceKm, double hours) {
        static final Verdict OK = new Verdict(Level.NONE, null, null, 0, 0);
    }

    private final UserBehaviorLogRepository logs;
    private final double maxSpeedKmh;
    private final double minDistanceKm;
    private final int minHistory;

    public FraudEngine(
            UserBehaviorLogRepository logs,
            @Value("${nexorix.fraud.max-speed-kmh:900}") double maxSpeedKmh,
            @Value("${nexorix.fraud.min-distance-km:150}") double minDistanceKm,
            @Value("${nexorix.fraud.min-history:5}") int minHistory
    ) {
        this.logs = logs;
        this.maxSpeedKmh = maxSpeedKmh;
        this.minDistanceKm = minDistanceKm;
        this.minHistory = minHistory;
    }

    public Verdict evaluate(User user, Observation obs) {
        Verdict travel = impossibleTravel(user, obs);
        if (travel.level() != Level.NONE) {
            return travel;
        }
        if (obs.kind() == BankMovementKind.EXPENSE) {
            String key = key(obs.merchant());
            if (!key.isEmpty() && !logs.existsByUserIdAndMerchantKey(user.getId(), key)
                    && logs.countByUserId(user.getId()) >= minHistory) {
                return new Verdict(Level.ANOMALY, "Compra en un comercio inusual: " + obs.merchant(), null, 0, 0);
            }
        } else if (obs.kind() == BankMovementKind.THIRD_PARTY_TRANSFER) {
            String key = recipientKey(obs);
            if (!key.isEmpty() && !logs.existsByUserIdAndRecipientKey(user.getId(), key)) {
                return new Verdict(Level.ANOMALY, "Primera transferencia a " + obs.recipientName(), null, 0, 0);
            }
        }
        return Verdict.OK;
    }

    private Verdict impossibleTravel(User user, Observation obs) {
        if (obs.geo() == null) {
            return Verdict.OK;
        }
        UserBehaviorLog last = logs.findFirstByUserIdAndLatitudeNotNullAndOccurredAtBeforeOrderByOccurredAtDesc(
                user.getId(), obs.at().plusNanos(1)).orElse(null);
        if (last == null) {
            return Verdict.OK;
        }
        double km = last.geo().distanceKm(obs.geo());
        double hours = Math.max(0, Duration.between(last.getOccurredAt(), obs.at()).toSeconds() / 3600.0);
        boolean impossible = km >= minDistanceKm && (hours == 0 || km / hours > maxSpeedKmh);
        if (!impossible) {
            return Verdict.OK;
        }
        return new Verdict(Level.CRITICAL, String.format(Locale.ROOT,
                "Imposibilidad física: %s -> %s (%.0f km) en %.1f h", last.geo().label(), obs.geo().label(), km, hours),
                last.geo(), km, hours);
    }

    /** Aprende de un movimiento aceptado: lo agrega al historial de comportamiento. */
    public void learn(User user, Observation obs) {
        if (obs.kind() == BankMovementKind.EXPENSE) {
            logs.save(new UserBehaviorLog(user, UserBehaviorLog.EventType.PURCHASE, obs.at())
                    .merchant(key(obs.merchant()), obs.merchant(), obs.category())
                    .amount(obs.amount()).place(obs.geo(), obs.ip()));
        } else if (obs.kind() == BankMovementKind.THIRD_PARTY_TRANSFER) {
            logs.save(new UserBehaviorLog(user, UserBehaviorLog.EventType.TRANSFER, obs.at())
                    .recipient(recipientKey(obs), obs.recipientName())
                    .amount(obs.amount()).place(obs.geo(), obs.ip()));
        }
    }

    private static String recipientKey(Observation obs) {
        String ref = obs.recipientRef();
        return key(ref != null && !ref.isBlank() ? ref : obs.recipientName());
    }

    /** "Éxito Calle 80 " -> "exito calle 80". */
    static String key(String text) {
        if (text == null) {
            return "";
        }
        String clean = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
