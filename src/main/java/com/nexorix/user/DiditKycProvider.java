package com.nexorix.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente de Didit. Crea dos tipos de sesion con el mismo workflow
 * (documento + selfie), que solo cambian a donde vuelve la persona:
 *
 * 1. Verificacion de identidad al registrarse  -> /kyc-resultado.html
 * 2. Verificacion para recuperar la cuenta      -> /recuperar.html
 */
@Component
@Primary
public class DiditKycProvider implements KycProvider {

    private static final Logger log =
            LoggerFactory.getLogger(DiditKycProvider.class);

    static final String KYC_CALLBACK_PATH = "/kyc-resultado.html";
    static final String RECOVERY_CALLBACK_PATH = "/recuperar.html";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String workflowId;
    private final String publicUrl;

    public DiditKycProvider(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${didit.api.base-url}") String baseUrl,
            @Value("${didit.api.key:}") String apiKey,
            @Value("${didit.workflow-id:}") String workflowId,
            @Value("${nexorix.public-url:http://localhost:8080}") String publicUrl
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = withoutTrailingSlash(clean(baseUrl));
        this.apiKey = clean(apiKey);
        this.workflowId = clean(workflowId);

        String cleanPublicUrl = withoutTrailingSlash(clean(publicUrl));
        this.publicUrl = cleanPublicUrl.isEmpty() ? "http://localhost:8080" : cleanPublicUrl;

        // Diagnostico al arrancar. NUNCA se imprimen los valores secretos.
        log.info("Didit base-url: {}", this.baseUrl);
        log.info("Didit api key configurada: {} ({} caracteres)",
                !this.apiKey.isEmpty(), this.apiKey.length());
        log.info("Didit workflow_id configurado: {} ({} caracteres)",
                !this.workflowId.isEmpty(), this.workflowId.length());
        log.info("Didit callback: {}", this.publicUrl + KYC_CALLBACK_PATH);
    }

    // ============================================================
    // 1. VERIFICACION DE IDENTIDAD
    // ============================================================

    @Override
    public KycProviderResponse startVerification(
            String documentType,
            String documentNumber,
            String userReference
    ) {
        Map<String, Object> expectedDetails = new LinkedHashMap<>();
        expectedDetails.put("document_number", documentNumber);

        return createSession(
                workflowId,
                "DIDIT_WORKFLOW_ID",
                userReference,
                KYC_CALLBACK_PATH,
                Map.of("expected_details", expectedDetails),
                "Sesion de verificacion creada correctamente en Didit."
        );
    }

    // ============================================================
    // 2. RECUPERAR LA CUENTA
    // ============================================================

    /**
     * Misma verificacion (documento + selfie) pero vuelve a /recuperar.html.
     * Nexorix revisa despues que el documento sea el de la cuenta.
     */
    public KycProviderResponse startRecoverySession(String userReference, String documentNumber) {

        Map<String, Object> expectedDetails = new LinkedHashMap<>();
        expectedDetails.put("document_number", documentNumber);

        return createSession(
                workflowId,
                "DIDIT_WORKFLOW_ID",
                userReference,
                RECOVERY_CALLBACK_PATH,
                Map.of("expected_details", expectedDetails),
                "Verificacion para recuperar la cuenta creada en Didit."
        );
    }

    // ============================================================
    // COMUN
    // ============================================================

    private KycProviderResponse createSession(
            String workflow,
            String workflowVariableName,
            String userReference,
            String callbackPath,
            Map<String, Object> extraFields,
            String successMessage
    ) {
        validateConfiguration(workflow, workflowVariableName);

        String url = baseUrl + "/v3/session/";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("workflow_id", workflow);
        requestBody.put("vendor_data", userReference);

        // Al terminar, Didit devuelve a la persona a esta pagina de Nexorix.
        requestBody.put("callback", publicUrl + callbackPath);

        // Redirigir en el dispositivo que inicio (computador) Y en el que
        // termino (celular, si escaneo el QR).
        requestBody.put("callback_method", "both");

        // Interfaz de Didit en espanol.
        requestBody.put("language", "es");

        requestBody.putAll(extraFields);

        // Convertimos el cuerpo a texto JSON nosotros mismos para saber
        // exactamente que se envia y su tamano.
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-api-key", apiKey);

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        log.info("Creando sesion en Didit: POST {}", url);
        log.info("Cuerpo enviado: {} bytes, incluye workflow_id: {}",
                jsonBody.getBytes(StandardCharsets.UTF_8).length,
                jsonBody.contains("\"workflow_id\""));

        try {

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException(
                        "Didit respondio con HTTP " + response.getStatusCode().value());
            }

            String body = response.getBody();

            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Didit devolvio una respuesta vacia.");
            }

            JsonNode json = objectMapper.readTree(body);

            String sessionId = getRequiredText(json, "session_id");
            String verificationUrl = getRequiredText(json, "url");

            log.info("Sesion creada en Didit. session_id: {}", sessionId);

            // Diagnostico: Didit devuelve el callback que guardo. Si sale
            // vacio, Didit no devolvera a la persona a Nexorix al terminar.
            log.info("Didit guardo el callback: {}",
                    json.path("callback").asText("(vacio)"));

            return new KycProviderResponse(
                    "DIDIT",
                    "IN_PROGRESS",
                    sessionId,
                    successMessage,
                    verificationUrl
            );

        } catch (RestClientResponseException exception) {

            log.warn("Didit respondio HTTP {}: {}",
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString());

            throw new IllegalStateException(
                    "Didit respondio HTTP " + exception.getStatusCode().value()
                            + ": " + exception.getResponseBodyAsString(),
                    exception);

        } catch (IllegalStateException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "No fue posible crear la sesion en Didit: " + exception.getMessage(),
                    exception);
        }
    }

    private void validateConfiguration(String workflow, String workflowVariableName) {

        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("Falta configurar didit.api.base-url.");
        }

        if (apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno DIDIT_API_KEY "
                            + "en la configuracion de ejecucion.");
        }

        if (workflow.isEmpty()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno " + workflowVariableName
                            + " en la configuracion de ejecucion.");
        }
    }

    private String getRequiredText(JsonNode json, String field) {

        JsonNode value = json.get(field);

        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException(
                    "Didit no devolvio el campo requerido: " + field);
        }

        return value.asText();
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String clean(String value) {

        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        // Si alguien pego el valor con comillas en IntelliJ, se las quitamos.
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        return trimmed;
    }
}
