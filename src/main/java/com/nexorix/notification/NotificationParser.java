package com.nexorix.notification;

import com.nexorix.bank.Bank;
import com.nexorix.bank.BankCatalog;
import com.nexorix.importer.StatementValues;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entiende las notificaciones de los bancos colombianos ("Recibiste $50.000 de
 * Ana", "Compra por $25.000 en Exito"...). Si no es un movimiento de dinero
 * (promociones, claves, avisos) o no se entiende el monto, devuelve vacio:
 * mejor ignorarla que registrar algo mal.
 */
public final class NotificationParser {

    private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9.,]*)");
    private static final Pattern PARTY = Pattern.compile(
            "\\b(?:de|a|en|para)\\s+([\\p{L}0-9][\\p{L}0-9 &'.\\-]{1,50})");

    private static final String[] IGNORE = {"rechazad", "no fue posible", "fallid", "no se pudo", "codigo",
            "clave", "otp", "promocion", "gana ", "oferta", "te prestamos", "aprobado para"};
    private static final String[] INCOME = {"recibiste", "te enviaron", "te llegaron", "te llego", "te pagaron",
            "te transfirieron", "transferencia recibida", "abono", "consignacion", "deposito", "ingreso",
            "recibido"};
    private static final String[] EXPENSE = {"enviaste", "pagaste", "compra", "retiro", "pago de", "pago por",
            "transferiste", "transferencia enviada", "transferencia exitosa", "debito", "cobro", "avance",
            "enviado"};

    private NotificationParser() {
    }

    public static Optional<ParsedNotification> parse(String packageName, String title, String text) {
        String safeTitle = title == null ? "" : title;
        String safeText = text == null ? "" : text;
        String all = safeTitle + " " + safeText;
        String plain = StatementValues.stripAccents(all.toLowerCase());

        for (String word : IGNORE) {
            if (plain.contains(word)) {
                return Optional.empty();
            }
        }

        Optional<Bank> bank = BankCatalog.detect(packageName == null ? "" : packageName.replace('.', ' '),
                safeTitle, safeText);
        String type = direction(plain);
        if (bank.isEmpty() || type == null) {
            return Optional.empty();
        }

        BigDecimal amount = null;
        BigDecimal balance = null;
        Matcher money = MONEY.matcher(all);
        while (money.find()) {
            BigDecimal value = StatementValues.parseAmount(money.group(1));
            if (value == null || value.signum() <= 0) {
                continue;
            }
            String before = StatementValues.stripAccents(
                    all.substring(Math.max(0, money.start() - 25), money.start()).toLowerCase());
            if (before.contains("saldo") || before.contains("disponible")) {
                if (balance == null) {
                    balance = value;
                }
            } else if (amount == null) {
                amount = value;
            }
        }
        if (amount == null) {
            return Optional.empty();
        }

        String body = safeText.isBlank() ? safeTitle : safeText;
        boolean withdrawal = plain.contains("retiro");
        boolean sent = plain.contains("enviaste") || plain.contains("transferiste") || plain.contains("transferencia");
        boolean purchase = type.equals("EGRESO") && !sent
                && (withdrawal || plain.contains("compra") || plain.contains("pagaste") || plain.contains("pago"));
        String label = withdrawal ? "Retiro en cajero" : counterparty(body);
        return Optional.of(new ParsedNotification(bank.get(), type, amount, label, purchase, balance));
    }

    private static String direction(String plain) {
        int income = firstIndex(plain, INCOME);
        int expense = firstIndex(plain, EXPENSE);
        if (income < 0 && expense < 0) {
            return null;
        }
        if (income >= 0 && (expense < 0 || income <= expense)) {
            return "INGRESO";
        }
        return "EGRESO";
    }

    private static int firstIndex(String text, String[] words) {
        int best = -1;
        for (String word : words) {
            int at = text.indexOf(word);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }

    /** Persona o comercio que aparece despues del monto: "Recibiste $50.000 de Ana Perez". */
    private static String counterparty(String text) {
        int dollar = text.indexOf('$');
        Matcher party = PARTY.matcher(dollar >= 0 ? text.substring(dollar) : text);
        while (party.find()) {
            String candidate = party.group(1).trim().replaceAll("\\s+(con|el|la|en|por|a las?)\\s.*$", "").trim();
            if (candidate.length() >= 2 && !candidate.matches("(?i)^(tu|su|la|el|una?|tarjeta|cuenta)\\b.*")) {
                return StatementValues.cleanDescription(candidate);
            }
        }
        return null;
    }
}
