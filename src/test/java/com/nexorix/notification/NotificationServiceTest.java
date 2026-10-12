package com.nexorix.notification;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.banking.BankEventRepository;
import com.nexorix.banking.BankLink;
import com.nexorix.banking.BankLinkService;
import com.nexorix.banking.BankSyncService;
import com.nexorix.banking.BankWebhookPayload;
import com.nexorix.fraud.GeoLocator;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationTokenRepository tokens;
    private AccountRepository accounts;
    private AccountService accountService;
    private BankSyncService sync;
    private BankLinkService links;
    private NotificationLogRepository logs;
    private BankEventRepository events;
    private NotificationService service;
    private User ana;
    private NotificationToken stored;
    private BankLink link;

    @BeforeEach
    void setUp() {
        tokens = mock(NotificationTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        accounts = mock(AccountRepository.class);
        accountService = mock(AccountService.class);
        sync = mock(BankSyncService.class);
        links = mock(BankLinkService.class);
        logs = mock(NotificationLogRepository.class);
        events = mock(BankEventRepository.class);
        service = new NotificationService(tokens, users, accounts, accountService, sync, links, logs, events,
                new GeoLocator());

        ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(tokens.findByUserUsername("ana")).thenReturn(Optional.empty());
        when(accounts.findByUserUsername("ana")).thenReturn(List.of());
        when(tokens.save(any(NotificationToken.class))).thenAnswer(call -> {
            stored = call.getArgument(0);
            return stored;
        });
        link = new BankLink(ana, null, "NEQUI", "manual-nequi-000001");
        when(links.linkFor(any(), any())).thenReturn(link);
        when(sync.process(any())).thenReturn(new BankSyncService.Result("APPLIED", "INCOME", 1L, "NONE", null));
    }

    private String newToken() {
        String token = service.createToken("ana");
        when(tokens.findByTokenHash(NotificationService.hash(token))).thenReturn(Optional.of(stored));
        return token;
    }

    @Test
    void laLlaveSeGuardaSoloComoHuella() {
        String token = newToken();

        assertThat(token).startsWith("nxr_");
        assertThat(ReflectionTestUtils.getField(stored, "tokenHash")).isEqualTo(NotificationService.hash(token))
                .isNotEqualTo(token);
    }

    @Test
    void rechazaUnaLlaveQueNoExiste() {
        assertThatThrownBy(() -> service.ingest("nxr_falsa", "", "", "Nequi", "Recibiste $5.000 de Ana", null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.ingest(null, "", "", "Nequi", "Recibiste $5.000 de Ana", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void creaLaCuentaDelBancoSiNoExisteYEnviaElMovimientoAlFlujoBancario() {
        String token = newToken();
        Account created = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        when(accountService.createAccount("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, "ana")).thenReturn(created);

        var outcome = service.ingest(token, "com.nequi.MobileApp", "", "Nequi", "Recibiste $50.000 de Juan Perez", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.REGISTERED);
        ArgumentCaptor<BankWebhookPayload> sent = ArgumentCaptor.forClass(BankWebhookPayload.class);
        verify(sync).process(sent.capture());
        BankWebhookPayload p = sent.getValue();
        assertThat(p.direction()).isEqualTo("CREDIT");
        assertThat(p.channel()).isEqualTo("TRANSFER");
        assertThat(p.amount()).isEqualByComparingTo("50000");
        assertThat(p.counterparty().name()).isEqualTo("Juan Perez");
        assertThat(p.accountRef()).isEqualTo("manual-nequi-000001");
        assertThat(p.eventId()).startsWith("ntf-");
        assertThat(stored.getReceived()).isEqualTo(1);
    }

    @Test
    void unaCompraSeEnviaComoCompraConTarjeta() {
        String token = newToken();
        Account mine = new Account("Mi Nequi", "BILLETERA", "Nequi", new BigDecimal("1000"), ana);
        when(accounts.findByUserUsername("ana")).thenReturn(List.of(mine));

        service.ingest(token, "", "", "Nequi", "Pagaste $15.000 en Tienda Don Pepe", null);

        ArgumentCaptor<BankWebhookPayload> sent = ArgumentCaptor.forClass(BankWebhookPayload.class);
        verify(sync).process(sent.capture());
        assertThat(sent.getValue().direction()).isEqualTo("DEBIT");
        assertThat(sent.getValue().channel()).isEqualTo("POS");
        assertThat(sent.getValue().merchant()).isEqualTo("Tienda Don Pepe");
        verify(accountService, never()).createAccount(any(), any(), any(), any(), any());
    }

    @Test
    void elMismoAvisoDosVecesUsaElMismoEventoYNoSeRegistraDeNuevo() {
        String token = newToken();
        when(sync.process(any())).thenReturn(new BankSyncService.Result("DUPLICATE", null, null, "NONE", null));

        var outcome = service.ingest(token, "", "", "Nequi", "Recibiste $50.000 de Juan Perez", 1760000000000L);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.DUPLICATE);
        assertThat(stored.getReceived()).isZero();
    }

    @Test
    void siElAvisoTraeElSaldoLaCuentaQuedaConEseSaldo() {
        String token = newToken();
        Account mine = new Account("Mi Nequi", "BILLETERA", "Nequi", new BigDecimal("1000"), ana);
        when(accounts.findByUserUsername("ana")).thenReturn(List.of(mine));

        service.ingest(token, "", "", "Nequi", "Recibiste $50.000 de Ana. Saldo disponible: $250.000", null);

        assertThat(mine.getBalance()).isEqualByComparingTo("250000");
    }

    private NotificationLog lastLog() {
        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void loQueNoEsDeUnBancoNoSeGuardaNiSeRegistra() {
        String token = newToken();

        var outcome = service.ingest(token, "com.whatsapp", "WhatsApp", "Ana", "hola, como vas", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.IGNORED);
        verify(sync, never()).process(any());
        verify(logs, never()).save(any());
    }

    @Test
    void unAvisoDelBancoSinMovimientoQuedaEnElHistorialComoInformativo() {
        String token = newToken();

        var outcome = service.ingest(token, "x", "Bancolombia", "Bancolombia", "Actualiza tu app", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.IGNORED);
        assertThat(lastLog().getResult()).isEqualTo(NotificationLog.INFO);
        assertThat(lastLog().getBank()).isEqualTo("Bancolombia");
        assertThat(lastLog().getSnippet()).isNull();
        verify(sync, never()).process(any());
    }

    @Test
    void unaClaveNuncaGuardaSuTexto() {
        String token = newToken();

        service.ingest(token, "x", "Nequi", "Nequi", "Tu código de seguridad es 123456", null);

        assertThat(lastLog().getResult()).isEqualTo(NotificationLog.SENSITIVE);
        assertThat(lastLog().getSnippet()).isNull();
        assertThat(lastLog().getSummary()).doesNotContain("123456");
    }

    @Test
    void siPareceUnMovimientoPeroNoSeEntiendeSeGuardaElTextoParaRevisarlo() {
        String token = newToken();

        var outcome = service.ingest(token, "x", "Davivienda", "Davivienda", "Operacion por $120.000 en proceso", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.UNRECOGNIZED);
        assertThat(lastLog().getResult()).isEqualTo(NotificationLog.UNRECOGNIZED);
        assertThat(lastLog().getSnippet()).contains("$120.000");
        verify(sync, never()).process(any());
    }

    @Test
    void elMismoMovimientoAvisadoDosVecesSeguidasNoSeRegistraDeNuevo() {
        String token = newToken();
        when(events.existsByUserIdAndBankAndDirectionAndAmountAndLabelIgnoreCaseAndCreatedAtAfter(
                eq(1L), eq("NEQUI"), eq("CREDIT"), any(), eq("Juan Perez"), any(LocalDateTime.class))).thenReturn(true);

        var outcome = service.ingest(token, "x", "Nequi", "Nequi", "Recibiste $50.000 de Juan Perez", 1760000099999L);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.DUPLICATE);
        assertThat(lastLog().getResult()).isEqualTo(NotificationLog.DUPLICATE);
        verify(sync, never()).process(any());
    }

    @Test
    void siElAvisoNombraUnaCiudadSeEnviaLaUbicacionAlAntifraude() {
        String token = newToken();
        Account mine = new Account("Bancolombia", "AHORROS", "Bancolombia", BigDecimal.ZERO, ana);
        when(accounts.findByUserUsername("ana")).thenReturn(List.of(mine));

        service.ingest(token, "x", "Bancolombia", "Bancolombia", "Compra por $25.000 en BEST BUY MIAMI con tarjeta *1234", null);

        ArgumentCaptor<BankWebhookPayload> sent = ArgumentCaptor.forClass(BankWebhookPayload.class);
        verify(sync).process(sent.capture());
        assertThat(sent.getValue().location().city()).isEqualTo("Miami");
        assertThat(sent.getValue().location().country()).isEqualTo("US");
        assertThat(sent.getValue().location().latitude()).isBetween(25.0, 26.0);
    }

    @Test
    void unMovimientoBloqueadoPorSeguridadQuedaEnElHistorialComoBloqueado() {
        String token = newToken();
        when(sync.process(any())).thenReturn(new BankSyncService.Result("BLOCKED", "EXPENSE", 9L, "CRITICAL", "Imposibilidad física"));

        service.ingest(token, "x", "Bancolombia", "Bancolombia", "Compra por $3.400.000 en BEST BUY MIAMI", null);

        assertThat(lastLog().getResult()).isEqualTo(NotificationLog.BLOCKED);
        assertThat(lastLog().getSummary()).contains("Imposibilidad física");
    }
}
