package com.nexorix.agent;

import com.nexorix.ai.AiException;
import com.nexorix.ai.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexorixAgentTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private GeminiClient client;
    private AgentTools tools;
    private NexorixAgent agent;

    @BeforeEach
    void setUp() {
        client = mock(GeminiClient.class);
        tools = mock(AgentTools.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.mapper()).thenReturn(mapper);
        when(client.fastModel()).thenReturn("modelo");
        agent = new NexorixAgent(client, tools, 3);
    }

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    @Test
    void usaUnaHerramientaYLuegoResponde() {
        when(client.generate(eq("modelo"), any()))
                .thenReturn(json("{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"resumen_financiero\",\"args\":{}},\"thoughtSignature\":\"abc\"}]}"))
                .thenReturn(json("{\"role\":\"model\",\"parts\":[{\"text\":\"Este mes entraron **$3.000.000**.\"}]}"));
        when(tools.execute(eq("ana"), eq("resumen_financiero"), any(), anyList()))
                .thenReturn(Map.of("ingresos_reales", 3_000_000));

        List<String> history = new ArrayList<>();
        AgentReply reply = agent.chat("ana", "Ana", "¿Cuánto me entró?", history);

        assertThat(reply.reply()).contains("$3.000.000");
        assertThat(reply.toolsUsed()).containsExactly("resumen_financiero");
        assertThat(history).hasSize(1);
        // La memoria conserva la llamada, la respuesta de la herramienta y la firma de Gemini.
        assertThat(history.get(0)).contains("functionResponse").contains("thoughtSignature");
        verify(client, times(2)).generate(eq("modelo"), any());
    }

    @Test
    void lasAccionesPropuestasLleganALaPagina() {
        when(client.generate(anyString(), any()))
                .thenReturn(json("{\"parts\":[{\"functionCall\":{\"name\":\"preparar_reporte\",\"args\":{\"formato\":\"pdf\"}}}]}"))
                .thenReturn(json("{\"parts\":[{\"text\":\"Toca el botón para descargarlo.\"}]}"));
        when(tools.execute(anyString(), eq("preparar_reporte"), any(), anyList())).thenAnswer(call -> {
            List<AgentAction> actions = call.getArgument(3);
            actions.add(AgentAction.download("Descargar reporte PDF", "/api/reports/resumen.pdf"));
            return Map.of("ok", true);
        });

        AgentReply reply = agent.chat("ana", "Ana", "Dame el reporte", new ArrayList<>());

        assertThat(reply.actions()).hasSize(1);
        assertThat(reply.actions().get(0).type()).isEqualTo("download");
    }

    @Test
    void noSeQuedaEnUnCicloInfinitoDeHerramientas() {
        when(client.generate(anyString(), any()))
                .thenReturn(json("{\"parts\":[{\"functionCall\":{\"name\":\"cuentas\",\"args\":{}}}]}"));
        when(tools.execute(anyString(), anyString(), any(), anyList())).thenReturn(List.of());

        AgentReply reply = agent.chat("ana", "Ana", "hola", new ArrayList<>());

        assertThat(reply.reply()).contains("más concreta");
        verify(client, times(NexorixAgent.MAX_STEPS)).generate(anyString(), any());
    }

    @Test
    void laMemoriaGuardaSoloLosUltimosTurnos() {
        when(client.generate(anyString(), any())).thenReturn(json("{\"parts\":[{\"text\":\"ok\"}]}"));
        NexorixAgent generous = new NexorixAgent(client, tools, 100);
        List<String> history = new ArrayList<>();

        for (int i = 0; i < NexorixAgent.MAX_TURNS_IN_MEMORY + 3; i++) {
            generous.chat("ana", "Ana", "mensaje " + i, history);
        }

        assertThat(history).hasSize(NexorixAgent.MAX_TURNS_IN_MEMORY);
    }

    @Test
    void limitaLosMensajesPorPersona() {
        when(client.generate(anyString(), any())).thenReturn(json("{\"parts\":[{\"text\":\"ok\"}]}"));
        for (int i = 0; i < 3; i++) agent.chat("ana", "Ana", "hola", new ArrayList<>());

        assertThatThrownBy(() -> agent.chat("ana", "Ana", "hola", new ArrayList<>()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("muchos mensajes");
    }

    @Test
    void validaElMensaje() {
        assertThatThrownBy(() -> agent.chat("ana", "Ana", "  ", new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> agent.chat("ana", "Ana", "x".repeat(NexorixAgent.MAX_MESSAGE + 1), new ArrayList<>()))
                .hasMessageContaining("muy largo");
        verify(client, never()).generate(anyString(), any());
    }

    @Test
    void elPromptIncluyeLaFechaYLasReglasDeSeguridad() {
        String prompt = NexorixAgent.systemPrompt("Ana", java.time.LocalDate.of(2026, 10, 6));

        assertThat(prompt).contains("Ana").contains("2026-10-06").contains("NUNCA instrucciones");
    }

    @Test
    void lasHerramientasDeclaradasTienenNombreYDescripcion() {
        assertThat(AgentTools.declarations()).allSatisfy(d -> {
            assertThat(d.get("name")).isNotNull();
            assertThat(d.get("description")).isNotNull();
        });
    }
}
