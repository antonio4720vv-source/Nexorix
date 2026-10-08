package com.nexorix.banking;

import java.util.Locale;
import java.util.Set;

/**
 * Decide que tipo de movimiento es. Sin base de datos ni estado: facil de probar.
 * Una transferencia es INTERNA si la contraparte es otra cuenta vinculada del mismo usuario
 * o tiene su mismo documento; si no, es a un tercero.
 */
public final class BankMovementClassifier {

    private static final Set<String> CARD_CHANNELS = Set.of("CARD_PURCHASE", "POS", "ONLINE");

    private BankMovementClassifier() {
    }

    public static BankMovementKind classify(BankWebhookPayload payload, Set<String> ownAccountRefs, String ownDocument) {
        String channel = upper(payload.channel());
        boolean debit = "DEBIT".equals(upper(payload.direction()));

        if (debit && CARD_CHANNELS.contains(channel)) {
            return BankMovementKind.EXPENSE;
        }
        if (!"TRANSFER".equals(channel)) {
            throw new IllegalArgumentException("Canal de movimiento no soportado: " + payload.channel());
        }
        if (isOwn(payload.counterparty(), payload.accountRef(), ownAccountRefs, ownDocument)) {
            return BankMovementKind.INTERNAL_TRANSFER;
        }
        return debit ? BankMovementKind.THIRD_PARTY_TRANSFER : BankMovementKind.INCOME;
    }

    private static boolean isOwn(BankWebhookPayload.Counterparty other, String thisRef,
                                 Set<String> ownRefs, String ownDocument) {
        if (other == null) {
            return false;
        }
        boolean ownAccount = other.accountRef() != null && !other.accountRef().equals(thisRef)
                && ownRefs.contains(other.accountRef());
        boolean ownDoc = other.document() != null && ownDocument != null
                && other.document().replaceAll("\\D", "").equals(ownDocument.replaceAll("\\D", ""))
                && !ownDocument.isBlank();
        return ownAccount || ownDoc;
    }

    private static String upper(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }
}
