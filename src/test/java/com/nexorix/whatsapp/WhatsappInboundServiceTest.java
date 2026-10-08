package com.nexorix.whatsapp;

import com.nexorix.ai.AiException;
import com.nexorix.user.ProcessedWebhookEventRepository;
import com.nexorix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsappInboundServiceTest {

    private static final String PHONE = "573001234567";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private WhatsappLinkRepository linkRepository;
    private PurchaseNoteRepository noteRepository;
    private ProcessedWebhookEventRepository processedRepository;
    private PurchaseNoteService noteService;
    private PurchaseAnswerReader reader;
    private WhatsappClient whatsapp;
    private WhatsappInboundService service;

    private User ana;
    private WhatsappLink link;
    private PurchaseNote note;
    private List<PurchaseColumn> columns;

    @BeforeEach
    void setUp() {
        linkRepository = mock(WhatsappLinkRepository.class);
        noteRepository = mock(PurchaseNoteRepository.class);
        processedRepository = mock(ProcessedWebhookEventRepository.class);
        noteService = mock(PurchaseNoteService.class);
        reader = mock(PurchaseAnswerReader.class);
        whatsapp = mock(WhatsappClient.class);

        service = new WhatsappInboundService(linkRepository, noteRepository, processedRepository, noteService,
                reader, whatsapp, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new SyncTaskExecutor());

        ana = new User("Ana María", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        link = new WhatsappLink(ana, PHONE);
        ReflectionTestUtils.setField(link, "id", 3L);
        link.markVerified();

        note = new PurchaseNote(ana, 50L, new BigDecimal("25000"), "Exito Calle 80", LocalDateTime.now());
        ReflectionTestUtils.setField(note, "id", 9L);
        note.questionSent("wamid.pregunta");

        columns = List.of(new PurchaseColumn(ana, "producto", "Producto", null, 0),
                new PurchaseColumn(ana, "tienda", "Tienda", null, 1));

        when(linkRepository.findByPhone(PHONE)).thenReturn(Optional.of(link));
        when(linkRepository.findById(3L)).thenReturn(Optional.of(link));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));
        when(noteService.columnsOf(any())).thenReturn(columns);
        when(noteService.writeValues(any())).thenAnswer(call -> mapper.writeValueAsString(call.getArgument(0)));
        when(reader.isEnabled()).thenReturn(true);
    }

    private static WhatsappInboundService.Incoming voice(String replyTo) {
        return new WhatsappInboundService.Incoming("wamid.audio", PHONE, "audio", "", "media-1",
                "audio/ogg; codecs=opus", replyTo);
    }

    private static WhatsappInboundService.Incoming text(String body) {
        return new WhatsappInboundService.Incoming("wamid.texto", PHONE, "text", body, "", "", "");
    }

    @Test
    void leeLosMensajesDelWebhookDeMeta() {
        String json = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":{
                  "messages":[{"from":"573001234567","id":"wamid.A","type":"audio",
                    "audio":{"id":"media-1","mime_type":"audio/ogg; codecs=opus","voice":true},
                    "context":{"id":"wamid.pregunta"}}],
                  "statuses":[{"id":"wamid.X","status":"read"}]}}]}]}
                """;

        List<WhatsappInboundService.Incoming> messages = WhatsappInboundService.parse(mapper.readTree(json));

        assertThat(messages).hasSize(1);
        WhatsappInboundService.Incoming message = messages.get(0);
        assertThat(message.type()).isEqualTo("audio");
        assertThat(message.mediaId()).isEqualTo("media-1");
        assertThat(message.replyToId()).isEqualTo("wamid.pregunta");
        assertThat(message.from()).isEqualTo(PHONE);
    }

    @Test
    void unaNotaDeVozLlenaLaTablaYConfirma() {
        when(noteRepository.findByQuestionMessageIdAndUserId("wamid.pregunta", 1L)).thenReturn(Optional.of(note));
        when(whatsapp.downloadMedia("media-1")).thenReturn(new WhatsappClient.Media(new byte[]{1}, "audio/ogg"));
        when(reader.readVoice(any(), any(), eq("audio/ogg; codecs=opus"))).thenReturn(new PurchaseAnswerReader.Answer(
                "Compré arroz Diana en el Éxito", Map.of("producto", "Arroz Diana", "tienda", "Éxito")));

        WhatsappInboundService.Outcome outcome = service.handle(voice("wamid.pregunta"));

        assertThat(outcome).isEqualTo(WhatsappInboundService.Outcome.ANSWERED);
        assertThat(note.getStatus()).isEqualTo(PurchaseNoteStatus.RESPONDIDA);
        assertThat(note.getAnswerType()).isEqualTo("VOZ");
        assertThat(note.getTranscript()).isEqualTo("Compré arroz Diana en el Éxito");
        assertThat(note.getValuesJson()).contains("Arroz Diana");
        verify(whatsapp).sendText(eq(PHONE), contains("Producto: Arroz Diana"));
    }

    @Test
    void sinCitarUsaLaPreguntaMasReciente() {
        when(noteRepository.findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(1L), eq(PurchaseNoteStatus.PREGUNTADA), any())).thenReturn(Optional.of(note));
        when(reader.readText(any(), eq("unas zapatillas"))).thenReturn(
                new PurchaseAnswerReader.Answer("unas zapatillas", Map.of("producto", "Zapatillas")));

        assertThat(service.handle(text("unas zapatillas"))).isEqualTo(WhatsappInboundService.Outcome.ANSWERED);
        assertThat(note.getAnswerType()).isEqualTo("TEXTO");
    }

    @Test
    void siNoHayPreguntasAbiertasLoDice() {
        when(noteRepository.findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.handle(text("hola"))).isEqualTo(WhatsappInboundService.Outcome.NO_OPEN_QUESTION);
        verify(whatsapp).sendText(eq(PHONE), contains("No tengo compras pendientes"));
    }

    @Test
    void siLaIaNoEntiendeLaPreguntaSigueAbierta() {
        when(noteRepository.findByQuestionMessageIdAndUserId("wamid.pregunta", 1L)).thenReturn(Optional.of(note));
        when(whatsapp.downloadMedia("media-1")).thenReturn(new WhatsappClient.Media(new byte[]{1}, "audio/ogg"));
        when(reader.readVoice(any(), any(), anyString())).thenThrow(new AiException("La IA está saturada"));

        assertThat(service.handle(voice("wamid.pregunta"))).isEqualTo(WhatsappInboundService.Outcome.NOT_UNDERSTOOD);
        assertThat(note.getStatus()).isEqualTo(PurchaseNoteStatus.PREGUNTADA);
        assertThat(note.getErrorMessage()).contains("saturada");
        verify(whatsapp).sendText(eq(PHONE), contains("otra vez"));
    }

    @Test
    void unNumeroDesconocidoNoRecibeRespuesta() {
        WhatsappInboundService.Incoming stranger = new WhatsappInboundService.Incoming(
                "wamid.z", "15550001111", "text", "hola", "", "", "");

        assertThat(service.handle(stranger)).isEqualTo(WhatsappInboundService.Outcome.UNKNOWN_PHONE);
        verify(whatsapp, never()).sendText(anyString(), anyString());
    }

    @Test
    void elCodigoCorrectoVerificaElNumero() {
        WhatsappLink pending = new WhatsappLink(ana, PHONE);
        ReflectionTestUtils.setField(pending, "id", 3L);
        pending.changePhone(PHONE, "123456", LocalDateTime.now().plusMinutes(10));
        when(linkRepository.findByPhone(PHONE)).thenReturn(Optional.of(pending));
        when(linkRepository.findById(3L)).thenReturn(Optional.of(pending));

        assertThat(service.handle(text("NEXORIX 999999"))).isEqualTo(WhatsappInboundService.Outcome.UNKNOWN_PHONE);
        assertThat(pending.isVerified()).isFalse();

        assertThat(service.handle(text("NEXORIX 123456"))).isEqualTo(WhatsappInboundService.Outcome.VERIFIED);
        assertThat(pending.isVerified()).isTrue();
        verify(whatsapp).sendText(eq(PHONE), contains("Ana"));
    }

    @Test
    void unCodigoVencidoNoVerifica() {
        WhatsappLink pending = new WhatsappLink(ana, PHONE);
        pending.changePhone(PHONE, "123456", LocalDateTime.now().minusMinutes(1));
        when(linkRepository.findByPhone(PHONE)).thenReturn(Optional.of(pending));

        assertThat(service.handle(text("NEXORIX 123456"))).isEqualTo(WhatsappInboundService.Outcome.UNKNOWN_PHONE);
        assertThat(pending.isVerified()).isFalse();
    }

    @Test
    void ignoraLosReintentosDeMeta() {
        when(processedRepository.existsByEventId("wamid.A")).thenReturn(true);
        String json = """
                {"entry":[{"changes":[{"value":{"messages":[{"from":"573001234567","id":"wamid.A","type":"text","text":{"body":"hola"}}]}}]}]}
                """;

        assertThat(service.receive(mapper.readTree(json))).isZero();
        verify(linkRepository, never()).findByPhone(anyString());
    }
}
