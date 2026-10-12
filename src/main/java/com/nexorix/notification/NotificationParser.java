package com.nexorix.notification;

import com.nexorix.bank.Bank;
import com.nexorix.bank.BankCatalog;
import com.nexorix.importer.StatementValues;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entiende las notificaciones de los bancos colombianos ("Recibiste $50.000 de Ana",
 * "Compra por $25.000 en Exito"...). Antes de interpretar nada decide de que se trata:
 * no es de un banco, trae una clave o codigo (nunca se guarda su texto), es informativa,
 * parece un movimiento pero no se entendio, o es un movimiento.
 */
public final class NotificationParser {

    /** De que se trata un aviso. */
    public enum Kind { NOT_BANK, SENSITIVE, INFO, UNRECOGNIZED, MOVEMENT }

    /** Resultado del analisis. bank y movement pueden ser null segun el tipo. */
    public record Analysis(Kind kind, Bank bank, ParsedNotification movement) {
    }

    private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9.,]*)");
    private static final Pattern PARTY = Pattern.compile(
            "\\b(?:de|a|en|para)\\s+([\\p{L}0-9][\\p{L}0-9 &'.\\-]{1,50})");

    /** Claves, codigos y datos con los que se puede entrar a una cuenta: no se guardan. */
    private static final String[] SENSITIVE = {"codigo", "clave", "otp", "contrasena", "token", "pin "};
    /** Avisos que no son un movimiento hecho: rechazos, promociones, ofertas. */
    private static final String[] NOT_A_MOVEMENT = {"rechazad", "no fue posible", "fallid", "no se pudo",
            "promocion", "gana ", "oferta", "te prestamos", "aprobado para"};
    private static final String[] INCOME = {"recibiste", "te enviaron", "te llegaron", "te llego", "te pagaron",
            "te transfirieron", "transferencia recibida", "abono", "consignacion", "deposito", "ingreso",
            "recibido"};
    private static final String[] EXPENSE = {"enviaste", "pagaste", "compra", "retiro", "pago de", "pago por",
            "transferiste", "transferencia enviada", "transferencia exitosa", "debito", "cobro", "avance",
            "enviado"};

    private NotificationParser() {
    }

    /** Atajo: el movimiento entendido, o vacio. */
    public static Optional<ParsedNotification> parse(String packageName, String appName, String title, String text) {
        return Optional.ofNullable(analyze(packageName, appName, title, text).movement());
    }

    /**
     * @param appName el nombre de la app en el celular ("Nequi", "Bancolombia"): es lo primero que se mira
     *                para saber el banco; si no alcanza, se busca en el paquete, el titulo y el texto.
     */
    public static Analysis analyze(String packageName, String appName, String title, String text) {
        String safeTitle = title == null ? "" : title;
        String safeText = text == null ? "" : text;
        String all = safeTitle + " " + safeText;
        String plain = StatementValues.stripAccents(all.toLowerCase());

        Optional<Bank> found = BankCatalog.detect(appName == null ? "" : appName)
                .or(() -> BankCatalog.detect(packageName == null ? "" : packageName.replace('.', ' '),
                        safeTitle, safeText));
        if (found.isEmpty()) {
            return new Analysis(Kind.NOT_BANK, null, null);
        }
        Bank bank = found.get();

        if (containsAny(plain, SENSITIVE)) {
            return new Analysis(Kind.SENSITIVE, bank, null);
        }
        if (containsAny(plain, NOT_A_MOVEMENT)) {
            return new Analysis(Kind.INFO, bank, null);
        }

        BigDecimal amount = null;
        BigDecimal balance = null;
        Matcher money = MONEY.matcher(all);
        boolean hasMoney = false;
        while (money.find()) {
            hasMoney = true;
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

        String type = direction(plain);
        if (type == null || amount == null) {
            // Con un monto pero sin entender que paso: se guarda para revisarlo. Sin monto: informativo.
            return new Analysis(hasMoney ? Kind.UNRECOGNIZED : Kind.INFO, bank, null);
        }

        String body = safeText.isBlank() ? safeTitle : safeText;
        boolean withdrawal = plain.contains("retiro");
        boolean sent = plain.contains("enviaste") || plain.contains("transferiste") || plain.contains("transferencia");
        boolean purchase = type.equals("EGRESO") && !sent
                && (withdrawal || plain.contains("compra") || plain.contains("pagaste") || plain.contains("pago"));
        String label = withdrawal ? "Retiro en cajero" : counterparty(body);
        return new Analysis(Kind.MOVEMENT, bank,
                new ParsedNotification(bank, type, amount, label, purchase, balance));
    }

    private static boolean containsAny(String text, String[] words) {
        return firstIndex(text, words) >= 0;
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
