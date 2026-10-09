package com.nexorix.dto;

import com.nexorix.user.User;

import java.util.Locale;

/** Datos de la cuenta para la ventana de perfil. Lo sensible sale ya censurado: nunca viaja completo. */
public record ProfileResponse(String name, String username, String accountNumber, String email,
                              String cedula, String phone, String kycStatus) {

    public static ProfileResponse fromUser(User user) {
        return new ProfileResponse(
                maskName(user.getName()),
                user.getUsername(),
                accountNumber(user.getPublicId()),
                maskEmail(user.getEmail()),
                maskKeepEnds(user.getCedula(), 2, 2),
                maskPhone(user.getSecurityPhone()),
                user.getKycStatus().name());
    }

    /** "NX-1A2B3C4D": las primeras 8 letras o numeros del identificador publico. */
    static String accountNumber(String publicId) {
        if (publicId == null) return "";
        String clean = publicId.replace("-", "").toUpperCase(Locale.ROOT);
        return "NX-" + clean.substring(0, Math.min(8, clean.length()));
    }

    /** "Antonio José" -> "Ant**** Jos***": 3 letras de cada palabra. */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (out.length() > 0) out.append(' ');
            int keep = Math.min(3, word.length());
            out.append(word, 0, keep).append("*".repeat(Math.max(word.length() - keep, 1)));
        }
        return out.toString();
    }

    /** "1012345602" -> "10******02". */
    static String maskKeepEnds(String value, int start, int end) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= start + end) return "*".repeat(value.length());
        return value.substring(0, start) + "*".repeat(value.length() - start - end)
                + value.substring(value.length() - end);
    }

    /** "+5331312345" -> "+53313*****": se ven 6 caracteres, el resto va oculto. */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.startsWith("+") ? phone : "+" + phone;
        int keep = Math.min(6, digits.length() - 1);
        return digits.substring(0, keep) + "*".repeat(Math.max(digits.length() - keep, 1));
    }

    /** "ana@gmail.com" -> "an*@gmail.com". */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        int keep = Math.min(2, local.length());
        return local.substring(0, keep) + "*".repeat(Math.max(local.length() - keep, 1)) + email.substring(at);
    }
}
