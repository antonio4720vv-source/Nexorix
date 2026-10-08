package com.nexorix.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de WhatsApp Cloud (Meta).
 *
 * Docs: https://developers.facebook.com/docs/whatsapp/cloud-api
 *
 * Regla de WhatsApp: un negocio solo puede escribir PRIMERO con una
 * plantilla aprobada por Meta. El texto libre solo se permite dentro de
 * las 24 horas siguientes al ultimo mensaje de la persona.
 */
@Component
public class WhatsappClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsappClient.class);

    private static final String GRAPH = "https://graph.facebook.com/";

    /** Notas de voz de hasta 10 MB (WhatsApp permite 16 MB; Gemini recibe hasta 20 MB en linea). */
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String token;
    private final String phoneNumberId;
    private final String apiVersion;

    public record Media(byte[] bytes, String mimeType) {
    }

    @Autowired
    public WhatsappClient(
            ObjectMapper objectMapper,
            @Value("${nexorix.whatsapp.token:}") String token,
            @Value("${nexorix.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${nexorix.whatsapp.api-version:v23.0}") String apiVersion
    ) {
        this(buildRestTemplate(), objectMapper, token, phoneNumberId, apiVersion);
    }

    /** Constructor para pruebas. */
    WhatsappClient(RestTemplate restTemplate, ObjectMapper objectMapper, String token,
                   String phoneNumberId, String apiVersion) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.token = token == null ? "" : token.trim();
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.apiVersion = apiVersion;

        log.info("WhatsApp configurado: {}", isEnabled());
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    public boolean isEnabled() {
        return !token.isEmpty() && !phoneNumberId.isEmpty();
    }

    // ============================================================
    // ENVIAR
    // ============================================================

    /** Mensaje con plantilla aprobada (para escribir primero). Devuelve el id del mensaje. */
    public String sendTemplate(String to, String templateName, String language, List<String> bodyParams) {
        List<Map<String, Object>> parameters = bodyParams.stream()
                .map(value -> Map.<String, Object>of("type", "text", "text", value))
                .toList();

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language));
        if (!parameters.isEmpty()) {
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }

        Map<String, Object> body = base(to);
        body.put("type", "template");
        body.put("template", template);
        return send(body);
    }

    /** Texto libre (solo dentro de las 24 horas despues del ultimo mensaje de la persona). */
    public String sendText(String to, String text) {
        Map<String, Object> body = base(to);
        body.put("type", "text");
        body.put("text", Map.of("body", text, "preview_url", false));
        return send(body);
    }

    private static Map<String, Object> base(String to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        return body;
    }

    private String send(Map<String, Object> body) {
        requireEnabled();
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders());
        String url = GRAPH + apiVersion + "/" + phoneNumberId + "/messages";
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            String id = objectMapper.readTree(response == null ? "{}" : response)
                    .path("messages").path(0).path("id").asText("");
            if (id.isEmpty()) {
                throw new WhatsappException("WhatsApp no devolvió el id del mensaje.");
            }
            return id;
        } catch (RestClientResponseException exception) {
            throw new WhatsappException(explain(exception), exception);
        } catch (RestClientException exception) {
            throw new WhatsappException("No fue posible comunicarse con WhatsApp.", exception);
        }
    }

    // ============================================================
    // DESCARGAR NOTAS DE VOZ
    // ============================================================

    /**
     * Descarga un audio que mando la persona. Son dos pasos: pedir la URL
     * temporal del archivo (dura 5 minutos) y bajarlo con el mismo token.
     */
    public Media downloadMedia(String mediaId) {
        requireEnabled();
        HttpEntity<Void> auth = new HttpEntity<>(authHeaders());
        try {
            String info = restTemplate.exchange(GRAPH + apiVersion + "/" + mediaId,
                    HttpMethod.GET, auth, String.class).getBody();
            JsonNode json = objectMapper.readTree(info == null ? "{}" : info);
            String url = json.path("url").asText("");
            if (url.isEmpty()) {
                throw new WhatsappException("WhatsApp no devolvió la dirección del audio.");
            }
            if (json.path("file_size").asLong(0) > MAX_AUDIO_BYTES) {
                throw new WhatsappException("La nota de voz es muy larga.");
            }
            // Solo se descarga desde los servidores de Meta, con el token.
            String host = URI.create(url).getHost();
            if (!fromMeta(host)) {
                throw new WhatsappException("Dirección de audio no esperada.");
            }

            ResponseEntity<byte[]> file = restTemplate.exchange(URI.create(url), HttpMethod.GET, auth, byte[].class);
            byte[] bytes = file.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new WhatsappException("El audio llegó vacío.");
            }
            if (bytes.length > MAX_AUDIO_BYTES) {
                throw new WhatsappException("La nota de voz es muy larga.");
            }
            return new Media(bytes, json.path("mime_type").asText("audio/ogg"));

        } catch (RestClientResponseException exception) {
            throw new WhatsappException(explain(exception), exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new WhatsappException("No fue posible descargar la nota de voz.", exception);
        }
    }

    // ============================================================

    /** Meta entrega los audios desde lookaside.fbsbx.com (y a veces desde sus CDN). */
    static boolean fromMeta(String host) {
        if (host == null) {
            return false;
        }
        String clean = host.toLowerCase(java.util.Locale.ROOT);
        return List.of("fbsbx.com", "fbcdn.net", "whatsapp.net", "facebook.com").stream()
                .anyMatch(domain -> clean.equals(domain) || clean.endsWith("." + domain));
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new WhatsappException("WhatsApp no está configurado (faltan WHATSAPP_TOKEN y WHATSAPP_PHONE_NUMBER_ID).");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String explain(RestClientResponseException exception) {
        String detail;
        try {
            detail = objectMapper.readTree(exception.getResponseBodyAsString())
                    .path("error").path("message").asText("");
        } catch (Exception ignored) {
            detail = "";
        }
        int status = exception.getStatusCode().value();
        log.warn("WhatsApp respondio HTTP {}: {}", status, detail);
        return switch (status) {
            case 401 -> "El token de WhatsApp no es válido o venció.";
            case 429 -> "WhatsApp limitó los envíos por ahora. Intenta más tarde.";
            default -> "WhatsApp rechazó la solicitud" + (detail.isEmpty() ? "." : ": " + detail);
        };
    }
}
