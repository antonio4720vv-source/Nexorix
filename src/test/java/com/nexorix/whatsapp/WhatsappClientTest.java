package com.nexorix.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsappClientTest {

    private static final String SEND = "https://graph.facebook.com/v23.0/12345/messages";

    private RestTemplate rest;
    private MockRestServiceServer server;
    private WhatsappClient client;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new WhatsappClient(rest, JsonMapper.builder().build(), "token", "12345", "v23.0");
    }

    @Test
    void mandaLaPlantillaConElMontoYLaDescripcion() {
        server.expect(requestTo(SEND))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(jsonPath("$.to").value("573001234567"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("pregunta_compra"))
                .andExpect(jsonPath("$.template.language.code").value("es"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("$ 25.000"))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("Exito"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        String id = client.sendTemplate("573001234567", "pregunta_compra", "es", List.of("$ 25.000", "Exito"));

        assertThat(id).isEqualTo("wamid.1");
        server.verify();
    }

    @Test
    void descargaLaNotaDeVozEnDosPasos() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/media-1"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"url\":\"https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=1\",\"mime_type\":\"audio/ogg; codecs=opus\",\"file_size\":3}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=1"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        WhatsappClient.Media media = client.downloadMedia("media-1");

        assertThat(media.bytes()).containsExactly(1, 2, 3);
        assertThat(media.mimeType()).startsWith("audio/ogg");
        server.verify();
    }

    @Test
    void noDescargaDeServidoresQueNoSonDeMeta() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/media-1"))
                .andRespond(withSuccess("{\"url\":\"https://atacante.com/x\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.downloadMedia("media-1"))
                .isInstanceOf(WhatsappException.class)
                .hasMessageContaining("no esperada");
    }

    @Test
    void soloAceptaDominiosDeMeta() {
        assertThat(WhatsappClient.fromMeta("lookaside.fbsbx.com")).isTrue();
        assertThat(WhatsappClient.fromMeta("mmg.whatsapp.net")).isTrue();
        assertThat(WhatsappClient.fromMeta("fbsbx.com.atacante.com")).isFalse();
        assertThat(WhatsappClient.fromMeta("malofbsbx.com")).isFalse();
        assertThat(WhatsappClient.fromMeta(null)).isFalse();
    }

    @Test
    void unTokenVencidoDaUnMensajeClaro() {
        server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"expired\"}}"));

        assertThatThrownBy(() -> client.sendText("573001234567", "hola"))
                .isInstanceOf(WhatsappException.class)
                .hasMessageContaining("token");
    }

    @Test
    void sinConfiguracionNoEstaActivo() {
        WhatsappClient empty = new WhatsappClient(rest, JsonMapper.builder().build(), "", "", "v23.0");
        assertThat(empty.isEnabled()).isFalse();
        assertThatThrownBy(() -> empty.sendText("57300", "x")).isInstanceOf(WhatsappException.class);
    }
}
