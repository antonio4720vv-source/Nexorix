package com.nexorix.fraud;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.whatsapp.WhatsappClient;
import com.nexorix.whatsapp.WhatsappLink;
import com.nexorix.whatsapp.WhatsappLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Alerta critica: sale por WhatsApp Y por SMS, en otro hilo y despues de guardar el bloqueo.
 * IGNORA whatsapp_notifications_enabled a proposito: es seguridad, no un aviso comun.
 * El numero es el celular de seguridad; si no lo hay, el WhatsApp verificado.
 */
@Component
public class FraudAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertNotifier.class);

    private final UserRepository users;
    private final WhatsappLinkRepository links;
    private final AppNotificationRepository notifications;
    private final WhatsappClient whatsapp;
    private final SmsClient sms;
    private final TransactionTemplate tx;
    private final TaskExecutor executor;

    public FraudAlertNotifier(UserRepository users, WhatsappLinkRepository links,
                              AppNotificationRepository notifications, WhatsappClient whatsapp, SmsClient sms,
                              TransactionTemplate tx, @Qualifier("whatsappExecutor") TaskExecutor executor) {
        this.users = users;
        this.links = links;
        this.notifications = notifications;
        this.whatsapp = whatsapp;
        this.sms = sms;
        this.tx = tx;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCritical(CriticalFraudAlertEvent event) {
        executor.execute(() -> deliver(event));
    }

    void deliver(CriticalFraudAlertEvent event) {
        List<String> channels = new ArrayList<>(List.of("APP"));
        try {
            String phone = tx.execute(status -> phoneOf(event.userId()));
            if (phone == null) {
                log.warn("Alerta critica del usuario {} sin celular: solo quedo en la app.", event.userId());
            } else {
                channels.add(attempt("WHATSAPP", () -> whatsapp.isEnabled()
                        ? (whatsapp.sendText(phone, event.whatsappText()) != null ? "WHATSAPP" : null)
                        : demo("WHATSAPP", phone, event.whatsappText())));
                channels.add(attempt("SMS", () -> sms.send(phone, event.smsText())
                        ? "SMS" : demo("SMS", phone, event.smsText())));
            }
        } catch (Exception exception) {
            log.error("Error enviando la alerta critica {}", event.notificationId(), exception);
        }
        String sent = String.join(",", channels.stream().filter(java.util.Objects::nonNull).toList());
        tx.executeWithoutResult(status -> notifications.findById(event.notificationId())
                .ifPresent(notification -> notification.setChannels(sent)));
    }

    private String phoneOf(Long userId) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        if (user.getSecurityPhone() != null && !user.getSecurityPhone().isBlank()) {
            return user.getSecurityPhone();
        }
        return links.findByUserId(userId).filter(WhatsappLink::isVerified).map(WhatsappLink::getPhone).orElse(null);
    }

    /** Cada canal falla por separado: si WhatsApp cae, el SMS igual sale. Devuelve el canal enviado o null. */
    private String attempt(String name, java.util.function.Supplier<String> send) {
        try {
            return send.get();
        } catch (RuntimeException exception) {
            log.warn("La alerta critica no salio por {}: {}", name, exception.getMessage());
            return null;
        }
    }

    /** Sin claves configuradas: solo queda en el log y se marca "(demo)" en la campanita. */
    private String demo(String channel, String phone, String text) {
        log.warn("[DEMO {}] a +{}: {}", channel, phone, text);
        return channel + "(demo)";
    }
}
