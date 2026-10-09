package com.nexorix.banking;

/** Compara celulares sin importar espacios, "+" ni indicativo: cuentan los ultimos 10 digitos. */
public final class PhoneMatcher {

    private PhoneMatcher() {
    }

    public static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    public static boolean same(String a, String b) {
        String x = digits(a), y = digits(b);
        if (x.length() < 7 || y.length() < 7) return false;
        int n = Math.min(10, Math.min(x.length(), y.length()));
        return x.substring(x.length() - n).equals(y.substring(y.length() - n));
    }
}
