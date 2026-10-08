package com.nexorix.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Cliente de la API de Gemini (Google AI Studio). Tiene nivel gratuito.
 *
 * Docs: https://ai.google.dev/api/generate-content
 *
 * Pensado para muchas personas a la vez:
 *  - Un semaforo limita las llamadas simultaneas (el nivel gratuito tiene
 *    pocas solicitudes por minuto).
 *  - Si la API responde "ocupada" (429, 500, 503) reintenta con espera creciente.
 *  - Tiempos maximos de conexion y respuesta.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RETRIES = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String fastModel;
    private final Semaphore slots;
    private final long waitForSlotSeconds;

    @Autowired
    public GeminiClient(
            ObjectMapper objectMapper,
            @Value("${nexorix.ai.api-key:}") String apiKey,
            @Value("${nexorix.ai.model:gemini-3.5-flash}") String model,
            @Value("${nexorix.ai.fast-model:gemini-3.5-flash-lite}") String fastModel,
            @Value("${nexorix.ai.max-concurrent:4}") int maxConcurrent,
            @Value("${nexorix.ai.timeout-seconds:90}") int timeoutSeconds
    ) {
        this(buildRestTemplate(timeoutSeconds), objectMapper, apiKey, model, fastModel, maxConcurrent, 60);
    }

    /** Constructor para pruebas. */
    GeminiClient(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey,
                 String model, String fastModel, int maxConcurrent, long waitForSlotSeconds) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.fastModel = fastModel;
        this.slots = new Semaphore(Math.max(1, maxConcurrent), true);
        this.waitForSlotSeconds = waitForSlotSeconds;

        log.info("IA (Gemini) configurada: {} | modelo: {} | rapido: {} | llamadas simultaneas: {}",
                isEnabled(), model, fastModel, maxConcurrent);
    }

    private static RestTemplate buildRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    public String fastModel() {
        return fastModel;
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }

    // ============================================================
    // PARTES DE UN MENSAJE
    // ============================================================

    public static Map<String, Object> text(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", text);
        return part;
    }

    /** Un PDF completo: Gemini lo "lee" como una persona (incluso si es escaneado). */
    public static Map<String, Object> pdf(String base64) {
        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("mimeType", "application/pdf");
        inline.put("data", base64);
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("inlineData", inline);
        return part;
    }

    public static Map<String, Object> content(String role, List<?> parts) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", role);
        content.put("parts", parts);
        return content;
    }

    // ============================================================
    // LLAMADAS
    // ============================================================

    /** Una pregunta simple: devuelve el texto. Con json=true pide la respuesta en JSON. */
    public String complete(String useModel, String system, List<Map<String, Object>> parts,
                           int maxTokens, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(text(system))));
        body.put("contents", List.of(content("user", new ArrayList<>(parts))));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("maxOutputTokens", maxTokens);
        config.put("temperature", 0.1);
        if (json) {
            config.put("responseMimeType", "application/json");
        }
        body.put("generationConfig", config);
        return textOf(generate(useModel, body));
    }

    /**
     * Llamada completa (para el agente con herramientas). Devuelve el contenido
     * del primer candidato tal cual (con sus partes text / functionCall).
     */
    public JsonNode generate(String useModel, Map<String, Object> body) {

        if (!isEnabled()) {
            throw new AiException("La IA no está configurada (falta GEMINI_API_KEY).");
        }

        boolean acquired;
        try {
            acquired = slots.tryAcquire(waitForSlotSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiException("Proceso interrumpido.");
        }
        if (!acquired) {
            throw new AiException("La IA está atendiendo muchas solicitudes. Intenta en un momento.");
        }

        try {
            return callWithRetries(useModel, body);
        } finally {
            slots.release();
        }
    }

    private JsonNode callWithRetries(String useModel, Map<String, Object> body) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey); // la clave va en un encabezado, nunca en la URL

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        String url = BASE + useModel + ":generateContent";

        for (int attempt = 1; ; attempt++) {
            try {
                String response = restTemplate.postForObject(url, request, String.class);
                return firstCandidate(response);

            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 503;

                if (!retryable || attempt >= MAX_RETRIES) {
                    log.warn("Gemini respondio HTTP {} (intento {}): {}", status, attempt,
                            abbreviate(exception.getResponseBodyAsString()));
                    throw new AiException(switch (status) {
                        case 429 -> "Se alcanzó el límite gratuito de la IA por ahora. Intenta en un minuto.";
                        case 503, 500 -> "La IA está saturada en este momento. Intenta en unos minutos.";
                        case 400 -> "La IA no pudo procesar la solicitud (revisa el modelo configurado).";
                        case 401, 403 -> "La clave de Gemini no es válida o no tiene permiso.";
                        case 404 -> "El modelo de Gemini configurado no existe: " + useModel;
                        default -> "La IA no pudo procesar la solicitud (HTTP " + status + ").";
                    }, exception);
                }
                sleep(attempt == 1 ? 2000 : 5000);

            } catch (AiException exception) {
                throw exception;

            } catch (Exception exception) {
                if (attempt >= MAX_RETRIES) {
                    throw new AiException("No fue posible comunicarse con la IA.", exception);
                }
                sleep(1000L * attempt);
            }
        }
    }

    /** El contenido del primer candidato, o un error claro si la IA no respondio. */
    JsonNode firstCandidate(String response) {
        JsonNode json = objectMapper.readTree(response == null ? "{}" : response);

        String blocked = json.path("promptFeedback").path("blockReason").asText("");
        if (!blocked.isEmpty()) {
            throw new AiException("La IA no quiso procesar este contenido (" + blocked + ").");
        }

        JsonNode candidate = json.path("candidates").path(0);
        JsonNode content = candidate.path("content");
        if (candidate.isMissingNode() || !content.path("parts").isArray()) {
            String reason = candidate.path("finishReason").asText("SIN_RESPUESTA");
            throw new AiException("La IA no devolvió respuesta (" + reason + ").");
        }
        return content;
    }

    /** Une las partes de texto (ignora los "pensamientos" del modelo). */
    public static String textOf(JsonNode content) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new AiException("La IA devolvió una respuesta vacía.");
        }
        return text.toString();
    }

    /** Saca el JSON de una respuesta, aunque venga envuelto en ```json ... ```. */
    public JsonNode parseJson(String text) {
        String clean = text.replace("```json", "").replace("```", "").trim();
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiException("La IA no devolvió datos en el formato esperado.");
        }
        try {
            return objectMapper.readTree(clean.substring(start, end + 1));
        } catch (Exception exception) {
            throw new AiException("La IA no devolvió datos en el formato esperado.", exception);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiException("Proceso interrumpido.");
        }
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
