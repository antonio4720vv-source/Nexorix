package com.nexorix.user;

import com.nexorix.auth.RecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiditWebhookServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private KycService kycService;
    private RecoveryService recoveryService;
    private ProcessedWebhookEventRepository repository;
    private DiditWebhookService service;

    @BeforeEach
    void setUp() {
        kycService = mock(KycService.class);
        recoveryService = mock(RecoveryService.class);
        repository = mock(ProcessedWebhookEventRepository.class);
        service = new DiditWebhookService(kycService, recoveryService, repository);

        // Por defecto, ninguna sesion es de una recuperacion de cuenta.
        when(recoveryService.processWebhook(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private static final String APPROVED = """
            {
              "event_id": "evento-1",
              "webhook_type": "status.updated",
              "session_id": "sesion-1",
              "status": "Approved",
              "environment": "sandbox"
            }
            """;

    @Test
    void procesaElWebhookYRegistraElEvento() {
        when(repository.existsByEventId("evento-1")).thenReturn(false);
        when(kycService.processDiditWebhook("sesion-1", "Approved", false))
                .thenReturn(true);

        DiditWebhookService.Result result = service.handle(json(APPROVED));

        assertThat(result).isEqualTo(DiditWebhookService.Result.PROCESSED);
        verify(repository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void unEventoRepetidoNoSeProcesaDosVeces() {
        when(repository.existsByEventId("evento-1")).thenReturn(true);

        DiditWebhookService.Result result = service.handle(json(APPROVED));

        assertThat(result).isEqualTo(DiditWebhookService.Result.DUPLICATE);
        verify(kycService, never()).processDiditWebhook(anyString(), anyString(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void sinCambiosTambienSeRegistraParaNoRepetirlo() {
        when(repository.existsByEventId("evento-1")).thenReturn(false);
        when(kycService.processDiditWebhook("sesion-1", "Approved", false))
                .thenReturn(false);

        DiditWebhookService.Result result = service.handle(json(APPROVED));

        assertThat(result).isEqualTo(DiditWebhookService.Result.NO_CHANGES);
        verify(repository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void elWebhookDePruebaNoTocaUsuariosNiSeRegistra() {
        JsonNode payload = json("""
                {
                  "event_id": "evento-test",
                  "webhook_type": "status.updated",
                  "session_id": "sesion-x",
                  "status": "Approved",
                  "metadata": { "test_webhook": true }
                }
                """);

        DiditWebhookService.Result result = service.handle(payload);

        assertThat(result).isEqualTo(DiditWebhookService.Result.TEST);
        verify(kycService, never()).processDiditWebhook(anyString(), anyString(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void otroTipoDeEventoSeIgnoraPeroSeRegistra() {
        when(repository.existsByEventId("evento-2")).thenReturn(false);

        JsonNode payload = json("""
                {
                  "event_id": "evento-2",
                  "webhook_type": "data.updated",
                  "session_id": "sesion-1"
                }
                """);

        DiditWebhookService.Result result = service.handle(payload);

        assertThat(result).isEqualTo(DiditWebhookService.Result.IGNORED_TYPE);
        verify(kycService, never()).processDiditWebhook(anyString(), anyString(), anyBoolean());
        verify(repository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void sinEventIdEsUnWebhookInvalido() {
        JsonNode payload = json("""
                { "webhook_type": "status.updated", "session_id": "s", "status": "Approved" }
                """);

        assertThatThrownBy(() -> service.handle(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event_id");
    }

    @Test
    void laVerificacionDeUnaRecuperacionNoTocaElKyc() {
        when(repository.existsByEventId("evento-1")).thenReturn(false);
        when(recoveryService.processWebhook(eq("sesion-1"), eq("Approved"), any()))
                .thenReturn(Optional.of(true));

        DiditWebhookService.Result result = service.handle(json(APPROVED));

        assertThat(result).isEqualTo(DiditWebhookService.Result.PROCESSED);
        verify(kycService, never()).processDiditWebhook(anyString(), anyString(), anyBoolean());
    }
}
