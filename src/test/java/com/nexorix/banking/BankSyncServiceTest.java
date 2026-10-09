package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.banking.BankWebhookPayload.Counterparty;
import com.nexorix.banking.BankWebhookPayload.Location;
import com.nexorix.fraud.AppNotification;
import com.nexorix.fraud.AppNotificationRepository;
import com.nexorix.fraud.CriticalFraudAlertEvent;
import com.nexorix.fraud.FraudEngine;
import com.nexorix.fraud.GeoLocator;
import com.nexorix.fraud.UserBehaviorLog;
import com.nexorix.fraud.UserBehaviorLogRepository;
import com.nexorix.transaction.PurchaseRegisteredEvent;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankSyncServiceTest {

    private final List<Object> published = new ArrayList<>();
    private final List<UserBehaviorLog> history = new ArrayList<>();
    private final List<AppNotification> inbox = new ArrayList<>();
    private TransactionService transactions;
    private BankEventRepository events;
    private BankSyncService service;
    private User ana;
    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        ana = new User("Ana", "ana", "ana@nexorix.com", "1020", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        Account nequi = new Account("Nequi", "BILLETERA", "Nequi", new BigDecimal("1000000"), ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        Account bancolombia = new Account("Bancolombia", "AHORROS", "Bancolombia", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(bancolombia, "id", 7L);

        BankLinkRepository links = mock(BankLinkRepository.class);
        BankLink nequiLink = new BankLink(ana, nequi, "NEQUI", "nequi-1");
        BankLink bancoLink = new BankLink(ana, bancolombia, "BANCOLOMBIA", "banco-1");
        when(links.findByExternalRef("nequi-1")).thenReturn(Optional.of(nequiLink));
        when(links.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(nequiLink, bancoLink));

        events = mock(BankEventRepository.class);
        when(events.save(any(BankEvent.class))).thenAnswer(call -> {
            BankEvent saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        transactions = mock(TransactionService.class);
        when(transactions.saveFromBank(any(), any(), anyString(), anyString(), any(), anyString())).thenAnswer(call -> {
            Transaction saved = new Transaction(call.getArgument(1), call.getArgument(2), call.getArgument(3),
                    call.getArgument(4), call.getArgument(0), call.getArgument(5));
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });

        // Repositorio de comportamiento en memoria: lo que se "aprende" se puede consultar despues.
        UserBehaviorLogRepository logs = mock(UserBehaviorLogRepository.class);
        when(logs.save(any(UserBehaviorLog.class))).thenAnswer(call -> {
            history.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(logs.countByUserId(anyLong())).thenAnswer(call -> (long) history.size());
        when(logs.existsByUserIdAndMerchantKey(anyLong(), anyString())).thenAnswer(call ->
                history.stream().anyMatch(l -> call.getArgument(1).equals(l.getMerchantKey())));
        when(logs.existsByUserIdAndRecipientKey(anyLong(), anyString())).thenAnswer(call ->
                history.stream().anyMatch(l -> call.getArgument(1).equals(l.getRecipientKey())));
        when(logs.findFirstByUserIdAndLatitudeNotNullAndOccurredAtBeforeOrderByOccurredAtDesc(anyLong(), any()))
                .thenAnswer(call -> history.stream()
                        .filter(l -> l.getLatitude() != null && l.getOccurredAt().isBefore(call.getArgument(1)))
                        .max(java.util.Comparator.comparing(UserBehaviorLog::getOccurredAt)));

        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        when(notifications.save(any(AppNotification.class))).thenAnswer(call -> {
            AppNotification saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            inbox.add(saved);
            return saved;
        });

        service = new BankSyncService(links, events, mock(UserRepository.class), transactions,
                new FraudEngine(logs, 900, 150, 5), new GeoLocator(), notifications, published::add,
                mock(com.nexorix.split.SplitShareRepository.class));
    }

    private BankWebhookPayload card(String id, String merchant, String city, String country, OffsetDateTime at) {
        return new BankWebhookPayload(id, "NEQUI", "nequi-1", "DEBIT", new BigDecimal("50000"), "COP",
                "CARD_PURCHASE", merchant, null, new Location(city, country, null, null, null), at);
    }

    private void seedHabits() {
        for (int i = 0; i < 5; i++) {
            service.process(card("seed" + i, "Éxito", "Bogotá", "CO", now.minusDays(10 - i)));
        }
        published.clear();
        inbox.clear();
    }

    @Test
    void unaCompraConTarjetaSiemprePreguntaAunConElFlagApagado() {
        assertThat(ana.isWhatsappNotificationsEnabled()).isFalse();

        service.process(card("a1", "Éxito", "Bogotá", "CO", now));

        assertThat(published).filteredOn(PurchaseRegisteredEvent.class::isInstance).hasSize(1);
    }

    @Test
    void laCompraConTarjetaQuedaMarcadaComoTarjeta() {
        Transaction[] last = new Transaction[1];
        when(transactions.saveFromBank(any(), any(), anyString(), anyString(), any(), anyString())).thenAnswer(call -> {
            last[0] = new Transaction(call.getArgument(1), call.getArgument(2), call.getArgument(3),
                    call.getArgument(4), call.getArgument(0), call.getArgument(5));
            ReflectionTestUtils.setField(last[0], "id", 51L);
            return last[0];
        });

        service.process(card("a5", "Éxito", "Bogotá", "CO", now));

        assertThat(last[0].getSource()).isEqualTo("CARD");
    }

    @Test
    void unaTransferenciaATerceroSinElFlagNoPregunta() {
        BankWebhookPayload toThird = new BankWebhookPayload("t9", "NEQUI", "nequi-1", "DEBIT", new BigDecimal("80000"),
                "COP", "TRANSFER", null, new Counterparty("Carlos", "nequi-9", "555"), null, now);

        service.process(toThird);

        assertThat(published).noneMatch(PurchaseRegisteredEvent.class::isInstance);
    }

    @Test
    void conElFlagEncendidoUnGastoPreguntaEnQueGasto() {
        ana.setWhatsappNotificationsEnabled(true);

        BankSyncService.Result result = service.process(card("a2", "Éxito", "Bogotá", "CO", now));

        assertThat(result.kind()).isEqualTo("EXPENSE");
        assertThat(published).hasSize(1);
        PurchaseRegisteredEvent ask = (PurchaseRegisteredEvent) published.get(0);
        assertThat(ask.description()).isEqualTo("Éxito");
        assertThat(ask.amount()).isEqualByComparingTo("50000");
    }

    @Test
    void transferenciaAUnTerceroPreguntaPeroLaInternaNo() {
        ana.setWhatsappNotificationsEnabled(true);
        BankWebhookPayload toThird = new BankWebhookPayload("t1", "NEQUI", "nequi-1", "DEBIT", new BigDecimal("80000"),
                "COP", "TRANSFER", null, new Counterparty("Carlos", "nequi-9", "555"), null, now);
        BankWebhookPayload toOwn = new BankWebhookPayload("t2", "NEQUI", "nequi-1", "DEBIT", new BigDecimal("80000"),
                "COP", "TRANSFER", null, new Counterparty("Yo", "banco-1", null), null, now);

        assertThat(service.process(toThird).kind()).isEqualTo("THIRD_PARTY_TRANSFER");
        assertThat(published).hasSize(1);
        published.clear();

        assertThat(service.process(toOwn).kind()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(published).isEmpty();
        verify(transactions, org.mockito.Mockito.times(2)).saveFromBank(any(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void imposibilidadFisicaBloqueaYAlertaAunConElFlagApagado() {
        assertThat(ana.isWhatsappNotificationsEnabled()).isFalse();
        service.process(card("p1", "Éxito", "Bogotá", "CO", now.minusHours(1)));
        published.clear();
        inbox.clear();

        BankSyncService.Result result = service.process(card("p2", "Best Buy", "Miami", "US", now));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.risk()).isEqualTo("CRITICAL");
        // No se guardo la transaccion (solo la compra de Bogota) y no se pregunta nada.
        verify(transactions, org.mockito.Mockito.times(1)).saveFromBank(any(), any(), anyString(), anyString(), any(), anyString());
        assertThat(published).hasSize(1).first().isInstanceOf(CriticalFraudAlertEvent.class);
        CriticalFraudAlertEvent alert = (CriticalFraudAlertEvent) published.get(0);
        assertThat(alert.smsText()).contains("Best Buy");
        assertThat(alert.whatsappText()).contains("Miami");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getSeverity()).isEqualTo(AppNotification.Severity.CRITICAL);
    }

    @Test
    void unaAnomaliaLeveSoloAvisaDentroDeLaApp() {
        seedHabits();

        BankSyncService.Result result = service.process(card("u1", "Joyería Diamante", "Bogotá", "CO", now));

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.risk()).isEqualTo("ANOMALY");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getSeverity()).isEqualTo(AppNotification.Severity.WARNING);
        assertThat(published).noneMatch(CriticalFraudAlertEvent.class::isInstance);
    }

    @Test
    void unEventoRepetidoNoSeProcesaDosVeces() {
        when(events.existsByEventId("dup")).thenReturn(true);

        BankSyncService.Result result = service.process(card("dup", "Éxito", "Bogotá", "CO", now));

        assertThat(result.status()).isEqualTo("DUPLICATE");
        verify(transactions, never()).saveFromBank(any(), any(), anyString(), anyString(), any(), anyString());
    }
}
