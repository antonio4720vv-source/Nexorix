package com.nexorix.split;

import com.nexorix.user.User;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decide a cual cobro de "Dividir gastos" corresponde un dinero que llego por el banco.
 * Sin base de datos: recibe los cobros abiertos de ese MISMO monto exacto.
 *
 *   - Si el banco dice quien pago (nombre o documento): solo cuenta quien coincida (el mas antiguo si hay varios).
 *   - Si no dice quien: solo se asigna cuando hay un unico cobro con ese monto.
 */
public final class SplitPaymentMatcher {

    private SplitPaymentMatcher() {
    }

    public static Optional<SplitShare> pick(List<SplitShare> sameAmount, String name, String document) {
        if (sameAmount == null || sameAmount.isEmpty()) {
            return Optional.empty();
        }
        boolean knowsWho = !blank(name) || !blank(document);
        if (!knowsWho) {
            return sameAmount.size() == 1 ? Optional.of(sameAmount.get(0)) : Optional.empty();
        }
        return sameAmount.stream().filter(s -> isThem(s.getParticipant(), name, document)).findFirst();
    }

    static boolean isThem(User person, String name, String document) {
        if (!blank(document) && digits(document).length() >= 5 && digits(document).equals(digits(person.getCedula()))) {
            return true;
        }
        if (blank(name)) {
            return false;
        }
        String given = plain(name);
        for (String part : plain(person.getName()).split(" ")) {
            if (part.length() >= 3 && given.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static String plain(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim();
    }

    private static String digits(String text) {
        return text == null ? "" : text.replaceAll("\\D", "");
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
