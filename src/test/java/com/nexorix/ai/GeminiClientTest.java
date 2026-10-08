package com.nexorix.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/modelo-rapido:generateContent";
    private static final String OK =
            "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hola\"}]}}]}";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private RestTemplate rest;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
    }

    private GeminiClient client(String key) {
        return new GeminiClient(rest, mapper, key, "modelo-grande", "modelo-rapido", 2, 1);
    }

    @Test
    void enviaLaPeticionCorrectaConLaClaveEnUnEncabezado() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("¿Cuánto gasté?"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("sistema"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        String answer = client("clave").complete("modelo-rapido", "sistema",
                List.of(GeminiClient.text("¿Cuánto gasté?")), 100, true);

        assertThat(answer).isEqualTo("Hola");
        server.verify();
    }

    @Test
    void siSeAlcanzaElLimiteGratuitoReintentaYLuegoResponde() {
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(times(1), requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client("clave").complete("modelo-rapido", "s", List.of(GeminiClient.text("x")), 10, false))
                .isEqualTo("Hola");
        server.verify();
    }

    @Test
    void unModeloInexistenteDaUnMensajeClaroSinReintentar() {
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client("clave").complete("modelo-rapido", "s", List.of(GeminiClient.text("x")), 10, false))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("no existe");
        server.verify();
    }

    @Test
    void contenidoBloqueadoDaUnErrorClaro() {
        assertThatThrownBy(() -> client("clave").firstCandidate("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("SAFETY");
    }

    @Test
    void ignoraLosPensamientosDelModelo() {
        var content = mapper.readTree("{\"parts\":[{\"text\":\"pensando...\",\"thought\":true},{\"text\":\"Respuesta\"}]}");

        assertThat(GeminiClient.textOf(content)).isEqualTo("Respuesta");
    }

    @Test
    void sinClaveLaIaEstaDesactivada() {
        GeminiClient client = client("");

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.complete("m", "s", List.of(), 10, false))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void entiendeJsonAunqueVengaEnvueltoEnTexto() {
        assertThat(client("clave").parseJson("Aquí está:\n```json\n{\"movements\":[]}\n```").has("movements")).isTrue();
    }
}
