package com.nexorix.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reporta una IP atacante a AbuseIPDB (https://www.abuseipdb.com).
 * Sin ABUSEIPDB_API_KEY no hace nada. El comentario nunca lleva datos de usuarios.
 */
@Component
public class AbuseIpDbReporter {

    private static final Logger log = LoggerFactory.getLogger(AbuseIpDbReporter.class);
    private static final URI ENDPOINT = URI.create("https://api.abuseipdb.com/api/v2/report");

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AbuseIpDbReporter(@Value("${nexorix.security.abuseipdb.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /**
     * @param categories codigos de AbuseIPDB separados por coma (18 fuerza bruta, 21 ataque web, 19 bot malo...)
     * @return true si AbuseIPDB acepto el reporte
     */
    public boolean report(String ip, String categories, String comment) {
        if (!enabled() || IpDefense.isPrivate(ip)) {
            return false;
        }
        try {
            String form = "ip=" + enc(ip) + "&categories=" + enc(categories)
                    + "&comment=" + enc(truncate(comment, 1000));
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Key", apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("IP {} reportada a AbuseIPDB", ip);
                return true;
            }
            log.warn("AbuseIPDB rechazo el reporte de {} (HTTP {})", ip, response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("No se pudo reportar {} a AbuseIPDB: {}", ip, exception.toString());
        }
        return false;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
