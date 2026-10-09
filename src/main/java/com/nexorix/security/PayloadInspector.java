package com.nexorix.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Busca firmas tipicas de ataque (SQL injection, XSS, recorrido de carpetas, Log4Shell...)
 * y lee el campo trampa de los formularios. Es una alarma, no el unico escudo:
 * la app igual usa consultas parametrizadas y escapa lo que muestra.
 */
public final class PayloadInspector {

    /** Campo oculto de los formularios: una persona nunca lo llena. */
    public static final String HONEYPOT_FIELD = "nx_website";

    private static final Pattern ATTACK = Pattern.compile(
            "(?i)(union\\s+(all\\s+)?select|\\bor\\s+1\\s*=\\s*1|'\\s*or\\s*'|\"\\s*or\\s*\"|;\\s*drop\\s+table"
                    + "|sleep\\s*\\(\\s*\\d|benchmark\\s*\\(|information_schema|<\\s*script|javascript\\s*:"
                    + "|\\bon(error|load|mouseover)\\s*=|\\.\\./|\\.\\.\\\\|%2e%2e|\\$\\{\\s*jndi:|/etc/passwd"
                    + "|cmd\\.exe|/bin/(ba)?sh\\b|base64_decode|\\bexec\\s*\\(|\\bxp_cmdshell)");

    /** Campos que nunca se revisan ni se guardan: pueden llevar cualquier caracter. */
    private static final Set<String> SECRET_FIELDS = Set.of("password", "newpassword", "pin", "code", "token");

    private static final ObjectMapper JSON = new ObjectMapper();

    private PayloadInspector() {
    }

    public record Result(boolean honeypotFilled, String suspicious, String safeBody) {
    }

    /** Revisa un cuerpo JSON. Si no es JSON valido, solo lo escanea como texto (sin guardarlo). */
    public static Result inspectBody(byte[] body) {
        String raw = new String(body, StandardCharsets.UTF_8);
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject()) {
                return new Result(false, firstMatch(raw), null);
            }
            boolean honeypot = !root.path(HONEYPOT_FIELD).asText("").isBlank();
            StringBuilder safe = new StringBuilder();
            String[] hit = {null};
            for (Map.Entry<String, JsonNode> field : root.properties()) {
                String key = field.getKey();
                if (SECRET_FIELDS.contains(key.toLowerCase())) {
                    continue;
                }
                String value = field.getValue().asText("");
                if (hit[0] == null) {
                    hit[0] = firstMatch(value);
                }
                safe.append(key).append('=').append(value).append("; ");
            }
            return new Result(honeypot, hit[0], safe.toString());
        } catch (RuntimeException exception) {
            return new Result(false, firstMatch(raw), null);
        }
    }

    /** La misma solicitud, con el cuerpo ya leido pero todavia disponible para el controlador. */
    public static jakarta.servlet.http.HttpServletRequest cached(jakarta.servlet.http.HttpServletRequest request, byte[] body) {
        return new CachedBodyRequest(request, body);
    }

    /** Revisa la direccion con sus parametros (ya decodificados). */
    public static String inspectUrl(String path, String query) {
        String text = path + (query == null ? "" : "?" + query);
        String hit = firstMatch(text);
        if (hit != null) {
            return hit;
        }
        try {
            return firstMatch(URLDecoder.decode(text, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return "URL con codificacion invalida";
        }
    }

    private static String firstMatch(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        var matcher = ATTACK.matcher(text);
        return matcher.find() ? "Coincide con: " + matcher.group().trim() : null;
    }
}
