package com.nexorix.fraud;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.whatsapp.WhatsappClient;
import com.nexorix.whatsapp.WhatsappException;
import com.nexorix.whatsapp.WhatsappLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FraudAlertNotifierTest {

    private final CriticalFraudAlertEvent event = new CriticalFraudAlertEvent(1L, 7L, "sms", "whatsapp");
    private WhatsappClient whatsapp;
    private SmsClient sms;
    private AppNotification notification;
    private FraudAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        // El flag de WhatsApp comun esta apagado: la alerta critica sale igual.
        ana.setWhatsappNotificationsEnabled(false);
        ana.setSecurityPhone("573001234567");
        UserRepository users = mock(UserRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(ana));

        notification = new AppNotification(ana, AppNotification.Severity.CRITICAL, "t", "b", 1L);
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        when(notifications.findById(7L)).thenReturn(Optional.of(notification));

        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        org.mockito.Mockito.doAnswer(call -> {
            ((java.util.function.Consumer<?>) call.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        whatsapp = mock(WhatsappClient.class);
        sms = mock(SmsClient.class);
        notifier = new FraudAlertNotifier(users, mock(WhatsappLinkRepository.class), notifications, whatsapp, sms,
                tx, Runnable::run);
    }

    @Test
    void sinClavesLaAlertaQuedaComoDemoEnAmbosCanales() {
        when(whatsapp.isEnabled()).thenReturn(false);
        when(sms.send("573001234567", "sms")).thenReturn(false);

        notifier.deliver(event);

        assertThat(notification.getChannels()).isEqualTo("APP,WHATSAPP(demo),SMS(demo)");
    }

    @Test
    void conClavesSaleSiempreFlagApagadoPorWhatsappYSms() {
        when(whatsapp.isEnabled()).thenReturn(true);
        when(whatsapp.sendText("573001234567", "whatsapp")).thenReturn("wamid.1");
        when(sms.send("573001234567", "sms")).thenReturn(true);

        notifier.deliver(event);

        assertThat(notification.getChannels()).isEqualTo("APP,WHATSAPP,SMS");
        verify(sms).send(eq("573001234567"), eq("sms"));
    }

    @Test
    void siWhatsappFallaElSmsIgualSale() {
        when(whatsapp.isEnabled()).thenReturn(true);
        when(whatsapp.sendText(any(), any())).thenThrow(new WhatsappException("caido"));
        when(sms.send(any(), any())).thenReturn(true);

        notifier.deliver(event);

        assertThat(notification.getChannels()).isEqualTo("APP,SMS");
    }
}
