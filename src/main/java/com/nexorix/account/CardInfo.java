package com.nexorix.account;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Lo unico que Nexorix conserva de una tarjeta: marca, ultimos 4 y vencimiento. El numero completo
 * solo se usa aqui para validarlo (Luhn) y se descarta; el CVV ni se pide ni se guarda jamas.
 */
public record CardInfo(String brand, String last4, int expMonth, int expYear) {

    /** Valida y reduce. numero y vencimiento ("MM/AA" o "MM/AAAA") pueden venir con espacios o guiones. */
    public static CardInfo of(String number, String expiry, LocalDate today) {
        String digits = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{13,19}") || !luhn(digits)) {
            throw new IllegalArgumentException("El número de la tarjeta no es válido.");
        }
        String[] parts = expiry == null ? new String[0] : expiry.trim().split("/");
        if (parts.length != 2 || !parts[0].trim().matches("\\d{1,2}") || !parts[1].trim().matches("\\d{2}|\\d{4}")) {
            throw new IllegalArgumentException("El vencimiento debe tener el formato MM/AA.");
        }
        int month = Integer.parseInt(parts[0].trim());
        int year = Integer.parseInt(parts[1].trim());
        if (year < 100) year += 2000;
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes de vencimiento no es válido.");
        }
        if (YearMonth.of(year, month).atEndOfMonth().isBefore(today)) {
            throw new IllegalArgumentException("Esa tarjeta ya está vencida.");
        }
        if (year > today.getYear() + 15) {
            throw new IllegalArgumentException("El año de vencimiento no es válido.");
        }
        return new CardInfo(brand(digits), digits.substring(digits.length() - 4), month, year);
    }

    static String brand(String d) {
        if (d.startsWith("4")) return "VISA";
        if (d.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (d.matches("^3[47].*")) return "AMEX";
        if (d.matches("^(30[0-5]|36|38).*")) return "DINERS";
        return "OTRA";
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (dbl) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
