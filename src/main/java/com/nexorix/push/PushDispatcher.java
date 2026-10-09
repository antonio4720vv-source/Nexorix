package com.nexorix.push;

import com.nexorix.fraud.AppNotification;
import com.nexorix.fraud.AppNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Convierte cada aviso de la campanita en una notificacion del dispositivo.
 *
 * Revisa cada pocos segundos los avisos que todavia no se empujaron; asi no importa
 * quien los cree (antifraude, compras, lo que venga): todos salen por push.
 */
@Component
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final AppNotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final PushService push;
    private final TransactionTemplate tx;

    public PushDispatcher(AppNotificationRepository notifications, PushSubscriptionRepository subscriptions,
                          PushService push, TransactionTemplate tx) {
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.push = push;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${nexorix.push.poll-ms:4000}", initialDelay = 15000)
    public void dispatchPending() {
        try {
            tx.executeWithoutResult(status -> {
                // Solo los de las ultimas horas: un aviso viejo no se empuja por tarde.
                List<AppNotification> pending = notifications.findPendingPush(LocalDateTime.now().minusHours(6));
                for (AppNotification notification : pending) {
                    notification.markPushed();
                    List<PushSubscription> targets = subscriptions.findByUserId(notification.getUser().getId());
                    if (!targets.isEmpty()) {
                        push.deliver(targets, payload(notification.getTitle(), notification.getBody(),
                                "/seguridad.html", "n" + notification.getId()));
                    }
                }
            });
        } catch (RuntimeException exception) {
            log.warn("No se pudieron enviar los avisos push pendientes: {}", exception.getMessage());
        }
    }

    /** JSON que lee el service worker (sw.js). */
    static String payload(String title, String body, String url, String tag) {
        return "{\"title\":" + json(title) + ",\"body\":" + json(body) + ",\"url\":" + json(url)
                + ",\"tag\":" + json(tag) + "}";
    }

    private static String json(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> { }
                case '\t' -> out.append(' ');
                default -> out.append(c < 0x20 ? ' ' : c);
            }
        }
        return out.append('"').toString();
    }
}
