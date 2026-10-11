package com.nexorix.bank;

import com.nexorix.importer.StatementValues;

import java.util.List;
import java.util.Optional;

/**
 * Bancos y billeteras reales de Colombia. Las cuentas solo se pueden crear
 * con uno de estos nombres, para que "Bancolombia", "bancolombia" y
 * "Bancolombia S.A." no terminen siendo tres bancos distintos.
 */
public final class BankCatalog {

    private static final List<Bank> BANKS = List.of(
            bank("Bancolombia", false, "bancolombia"),
            bank("Davivienda", false, "davivienda"),
            bank("Nequi", true, "nequi"),
            bank("Daviplata", true, "daviplata"),
            bank("Nu Colombia", false, "nu colombia", "nu", "nubank"),
            bank("Lulo Bank", false, "lulo bank", "lulo"),
            bank("BBVA", false, "bbva"),
            bank("Banco de Bogotá", false, "banco de bogota"),
            bank("Banco Popular", false, "banco popular"),
            bank("Scotiabank Colpatria", false, "scotiabank colpatria", "colpatria", "scotiabank"),
            bank("Banco Caja Social", false, "banco caja social", "caja social"),
            bank("Banco de Occidente", false, "banco de occidente"),
            bank("Banco AV Villas", false, "av villas", "avvillas"),
            bank("Itaú", false, "itau"),
            bank("Banco Agrario", false, "banco agrario"),
            bank("Banco Falabella", false, "banco falabella"),
            bank("Banco Pichincha", false, "banco pichincha"),
            bank("Bancamía", false, "bancamia"),
            bank("Banco W", false, "banco w"),
            bank("Banco Serfinanza", false, "serfinanza"),
            bank("Banco GNB Sudameris", false, "gnb sudameris", "sudameris"),
            bank("Bancoomeva", false, "bancoomeva"),
            bank("Banco Finandina", false, "finandina"),
            bank("Movii", true, "movii"),
            bank("RappiPay", true, "rappipay", "rappi pay")
    );

    private BankCatalog() {
    }

    private static Bank bank(String name, boolean wallet, String... aliases) {
        return new Bank(name, wallet, List.of(aliases));
    }

    public static List<Bank> all() {
        return BANKS;
    }

    /** El banco con ese nombre (sin importar mayusculas ni tildes), o vacio si no existe. */
    public static Optional<Bank> byName(String name) {
        String wanted = StatementValues.normalizeForMatching(name);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return BANKS.stream()
                .filter(b -> StatementValues.normalizeForMatching(b.name()).equals(wanted))
                .findFirst();
    }

    /**
     * Busca que banco menciona un texto (titulo o cuerpo de una notificacion).
     * Gana el nombre mas largo ("banco popular" antes que "popular").
     */
    public static Optional<Bank> detect(String... texts) {
        String haystack = " " + StatementValues.normalizeForMatching(String.join(" ", texts)) + " ";
        Bank best = null;
        int bestLength = 0;
        for (Bank bank : BANKS) {
            for (String alias : bank.aliases()) {
                if (alias.length() > bestLength && haystack.contains(" " + alias + " ")) {
                    best = bank;
                    bestLength = alias.length();
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
