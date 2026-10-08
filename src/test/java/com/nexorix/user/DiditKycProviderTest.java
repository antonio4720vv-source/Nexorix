package com.nexorix.user;

import com.nexorix.config.DiditConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiditKycProviderTest {

    private static final String BASE_URL = "https://verification.didit.me";
    private static final String SESSION_URL = BASE_URL + "/v3/session/";
    private static final String FAKE_API_KEY = "clave-de-prueba";
    private static final String FAKE_WORKFLOW_ID = "11111111-2222-3333-4444-555555555555";
    private static final String PUBLIC_URL = "http://localhost:8080";

    private RestTemplate restTemplate;
    private MockRestServiceServer didit;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        didit = MockRestServiceServer.bindTo(restTemplate).build();
        objectMapper = JsonMapper.builder().build();
    }

    private DiditKycProvider provider(String apiKey, String workflowId) {
        return new DiditKycProvider(
                restTemplate, objectMapper, BASE_URL, apiKey, workflowId, PUBLIC_URL
        );
    }

    @Test
    void enviaWorkflowIdYDevuelveSesionCuandoDiditRespondeOk() {

        didit.expect(requestTo(SESSION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", FAKE_API_KEY))
                .andExpect(jsonPath("$.workflow_id").value(FAKE_WORKFLOW_ID))
                .andExpect(jsonPath("$.vendor_data").value("public-id-123"))
                .andExpect(jsonPath("$.expected_details.document_number").value("1000000000"))
                .andExpect(jsonPath("$.callback").value("http://localhost:8080/kyc-resultado.html"))
                .andExpect(jsonPath("$.language").value("es"))
                .andRespond(withSuccess(
                        "{\"session_id\":\"sess-abc\",\"url\":\"https://verify.didit.me/sess-abc\"}",
                        MediaType.APPLICATION_JSON
                ));

        KycProviderResponse response = provider(FAKE_API_KEY, FAKE_WORKFLOW_ID)
                .startVerification("CC", "1000000000", "public-id-123");

        assertThat(response.getProvider()).isEqualTo("DIDIT");
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getVerificationReference()).isEqualTo("sess-abc");
        assertThat(response.getVerificationUrl()).isEqualTo("https://verify.didit.me/sess-abc");

        didit.verify();
    }

    @Test
    void limpiaEspaciosYComillasDelWorkflowId() {

        didit.expect(requestTo(SESSION_URL))
                .andExpect(jsonPath("$.workflow_id").value(FAKE_WORKFLOW_ID))
                .andRespond(withSuccess(
                        "{\"session_id\":\"s1\",\"url\":\"https://x\"}",
                        MediaType.APPLICATION_JSON
                ));

        provider(FAKE_API_KEY, "  \"" + FAKE_WORKFLOW_ID + "\"  ")
                .startVerification("CC", "1000000000", "ref");

        didit.verify();
    }

    @Test
    void elCallbackFuncionaAunqueLaUrlPublicaTermineEnBarra() {

        DiditKycProvider conBarra = new DiditKycProvider(
                restTemplate, objectMapper, BASE_URL,
                FAKE_API_KEY, FAKE_WORKFLOW_ID, "https://nexorix.ngrok-free.dev/"
        );

        didit.expect(requestTo(SESSION_URL))
                .andExpect(jsonPath("$.callback")
                        .value("https://nexorix.ngrok-free.dev/kyc-resultado.html"))
                .andRespond(withSuccess(
                        "{\"session_id\":\"s1\",\"url\":\"https://x\"}",
                        MediaType.APPLICATION_JSON
                ));

        conBarra.startVerification("CC", "1000000000", "ref");

        didit.verify();
    }

    @Test
    void fallaSinLlamarADiditSiFaltaElWorkflowId() {

        assertThatThrownBy(() -> provider(FAKE_API_KEY, "")
                .startVerification("CC", "1000000000", "ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIDIT_WORKFLOW_ID");

        didit.verify();
    }

    @Test
    void fallaSinLlamarADiditSiFaltaLaApiKey() {

        assertThatThrownBy(() -> provider("   ", FAKE_WORKFLOW_ID)
                .startVerification("CC", "1000000000", "ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIDIT_API_KEY");

        didit.verify();
    }

    @Test
    void muestraElCuerpoDelErrorQueDevuelveDidit() {

        didit.expect(requestTo(SESSION_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"workflow_id\":[\"This field is required.\"]}"));

        assertThatThrownBy(() -> provider(FAKE_API_KEY, FAKE_WORKFLOW_ID)
                .startVerification("CC", "1000000000", "ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("This field is required.");
    }

    @Test
    void elRestTemplateDeNexorixUsaBuffering() {

        RestTemplate configured = new DiditConfig().restTemplate();

        assertThat(configured.getRequestFactory())
                .isInstanceOf(BufferingClientHttpRequestFactory.class);
    }

    @Test
    void laSesionDeRecuperacionVuelveARecuperarHtml() {

        didit.expect(requestTo(SESSION_URL))
                .andExpect(jsonPath("$.workflow_id").value(FAKE_WORKFLOW_ID))
                .andExpect(jsonPath("$.vendor_data").value("public-id-123"))
                .andExpect(jsonPath("$.callback").value("http://localhost:8080/recuperar.html"))
                .andExpect(jsonPath("$.callback_method").value("both"))
                .andExpect(jsonPath("$.expected_details.document_number").value("1000000009"))
                .andRespond(withSuccess(
                        "{\"session_id\":\"s1\",\"url\":\"https://x\"}",
                        MediaType.APPLICATION_JSON
                ));

        provider(FAKE_API_KEY, FAKE_WORKFLOW_ID)
                .startRecoverySession("public-id-123", "1000000009");

        didit.verify();
    }
}
