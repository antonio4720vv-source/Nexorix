package com.nexorix.push;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Suscripciones a las notificaciones del dispositivo (Web Push). */
@Service
public class PushService {

    static final int MAX_PER_USER = 10;

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final VapidKeys keys;
    private final WebPushClient client;

    public PushService(PushSubscriptionRepository subscriptions, UserRepository users, VapidKeys keys,
                       WebPushClient client) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.keys = keys;
        this.client = client;
    }

    public String publicKey() {
        return keys.publicKey();
    }

    @Transactional
    public void subscribe(String username, String endpoint, String p256dh, String auth) {
        User user = user(username);
        if (endpoint == null || p256dh == null || auth == null || endpoint.length() > 600
                || p256dh.length() > 150 || auth.length() > 60) {
            throw new IllegalArgumentException("La suscripción no es válida.");
        }
        if (!WebPushClient.isAllowedEndpoint(endpoint)) {
            throw new IllegalArgumentException("Este navegador usa un servicio de notificaciones no compatible.");
        }
        PushSubscription existing = subscriptions.findByEndpoint(endpoint).orElse(null);
        if (existing != null) {
            existing.update(user, p256dh, auth);
            return;
        }
        if (subscriptions.findByUserId(user.getId()).size() >= MAX_PER_USER) {
            throw new IllegalArgumentException("Ya tienes " + MAX_PER_USER + " dispositivos con avisos activados.");
        }
        subscriptions.save(new PushSubscription(user, endpoint, p256dh, auth));
    }

    @Transactional
    public void unsubscribe(String username, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, user(username).getId());
    }

    @Transactional(readOnly = true)
    public long devices(String username) {
        return subscriptions.findByUserId(user(username).getId()).size();
    }

    /** Aviso de prueba a todos los dispositivos de la persona. Devuelve a cuantos llego. */
    @Transactional
    public int sendTest(String username) {
        User user = user(username);
        String payload = PushDispatcher.payload("Nexorix", "Las notificaciones de tu dispositivo funcionan ✅", "/seguridad.html", "test");
        return deliver(subscriptions.findByUserId(user.getId()), payload);
    }

    /** Manda a cada suscripcion; borra las que ya no existen. */
    @Transactional
    public int deliver(List<PushSubscription> targets, String payload) {
        int sent = 0;
        for (PushSubscription subscription : targets) {
            WebPushClient.Outcome outcome = client.send(subscription, payload);
            if (outcome == WebPushClient.Outcome.SENT) {
                sent++;
            } else if (outcome == WebPushClient.Outcome.GONE) {
                subscriptions.delete(subscription);
            }
        }
        return sent;
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
