package com.nexorix.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Manda un aviso cifrado al servicio de push del navegador (Google, Mozilla, Apple, Microsoft). */
@Component
public class WebPushClient {

    private static final Logger log = LoggerFactory.getLogger(WebPushClient.class);

    /** Solo se le escribe a los servicios de push conocidos: el endpoint lo manda el navegador de la persona. */
    private static final List<String> ALLOWED_HOSTS = List.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
            "push.apple.com", "notify.windows.com", "push.microsoft.com");

    public enum Outcome { SENT, GONE, FAILED }

    private final VapidKeys keys;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public WebPushClient(VapidKeys keys) {
        this.keys = keys;
    }

    public static boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) {
                return false;
            }
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            return ALLOWED_HOSTS.stream().anyMatch(allowed -> lower.equals(allowed) || lower.endsWith("." + allowed));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** GONE = la suscripcion ya no existe (la persona la quito): hay que borrarla. */
    public Outcome send(PushSubscription subscription, String jsonPayload) {
        try {
            String endpoint = subscription.getEndpoint();
            if (!isAllowedEndpoint(endpoint)) {
                return Outcome.GONE;
            }
            URI uri = URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getAuthority();
            String jwt = WebPushCrypto.vapidJwt(audience, keys.subject(),
                    Instant.now().plusSeconds(12 * 3600).getEpochSecond(), keys.privateKey());
            byte[] body = WebPushCrypto.encrypt(jsonPayload.getBytes(StandardCharsets.UTF_8),
                    subscription.getP256dh(), subscription.getAuth());

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "vapid t=" + jwt + ", k=" + keys.publicKey())
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.SENT;
            }
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            log.warn("El servicio de push respondio {} para la suscripcion {}", status, subscription.getId());
            return Outcome.FAILED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException exception) {
            log.warn("No se pudo enviar el aviso push: {}", exception.getMessage());
            return Outcome.FAILED;
        }
    }
}
