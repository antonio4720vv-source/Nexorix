package com.nexorix.whatsapp;

import com.nexorix.ai.AiException;
import com.nexorix.ai.GeminiClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseAnswerReaderTest {

    private final GeminiClient client = new GeminiClient(JsonMapper.builder().build(), "", "m", "m", 1, 5);
    private final PurchaseAnswerReader reader = new PurchaseAnswerReader(client);
    private final List<PurchaseColumn> columns = List.of(
            new PurchaseColumn(null, "producto", "Producto", null, 0),
            new PurchaseColumn(null, "tienda", "Tienda", null, 1));

    @Test
    void soloGuardaLasColumnasDeLaPersonaYQuitaLosNull() {
        PurchaseAnswerReader.Answer answer = reader.parse("""
                ```json
                {"transcripcion":"Compré un mercado","valores":{"producto":"Mercado","tienda":null,"inventada":"x"}}
                ```""", columns, null);

        assertThat(answer.transcript()).isEqualTo("Compré un mercado");
        assertThat(answer.values()).containsOnlyKeys("producto").containsEntry("producto", "Mercado");
    }

    @Test
    void sinNadaUtilEsUnError() {
        assertThatThrownBy(() -> reader.parse("{\"transcripcion\":\"\",\"valores\":{}}", columns, null))
                .isInstanceOf(AiException.class);
    }

    @Test
    void conTextoUsaLaRespuestaComoTranscripcionSiLaIaNoLaDevuelve() {
        PurchaseAnswerReader.Answer answer = reader.parse("{\"valores\":{\"producto\":\"Pan\"}}", columns, "pan");
        assertThat(answer.transcript()).isEqualTo("pan");
    }

    @Test
    void limpiaElTipoDeAudioDeWhatsapp() {
        assertThat(PurchaseAnswerReader.cleanMime("audio/ogg; codecs=opus")).isEqualTo("audio/ogg");
        assertThat(PurchaseAnswerReader.cleanMime("audio/mpeg")).isEqualTo("audio/mpeg");
        assertThat(PurchaseAnswerReader.cleanMime(null)).isEqualTo("audio/ogg");
        assertThat(PurchaseAnswerReader.cleanMime("text/html")).isEqualTo("audio/ogg");
    }
}
