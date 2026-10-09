package com.nexorix.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;

/**
 * Llaves VAPID de Nexorix. En produccion van en NEXORIX_VAPID_PUBLIC / NEXORIX_VAPID_PRIVATE;
 * si faltan, se generan una vez y se guardan en la base de datos (para que las suscripciones
 * ya hechas sigan sirviendo despues de reiniciar).
 */
@Component
public class VapidKeys {

    private static final Logger log = LoggerFactory.getLogger(VapidKeys.class);

    private final PushSettingsRepository settings;
    private final String configuredPublic;
    private final String configuredPrivate;
    private final String subject;

    private String publicKey;
    private PrivateKey privateKey;

    public VapidKeys(PushSettingsRepository settings,
                     @Value("${nexorix.push.vapid-public:}") String configuredPublic,
                     @Value("${nexorix.push.vapid-private:}") String configuredPrivate,
                     @Value("${nexorix.push.subject:mailto:soporte@nexorix.app}") String subject) {
        this.settings = settings;
        this.configuredPublic = configuredPublic == null ? "" : configuredPublic.trim();
        this.configuredPrivate = configuredPrivate == null ? "" : configuredPrivate.trim();
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }

    /** Llave publica en base64url: es la "applicationServerKey" que usa el navegador. */
    public synchronized String publicKey() {
        load();
        return publicKey;
    }

    synchronized PrivateKey privateKey() {
        load();
        return privateKey;
    }

    private void load() {
        if (privateKey != null) {
            return;
        }
        try {
            if (!configuredPublic.isEmpty() && !configuredPrivate.isEmpty()) {
                publicKey = configuredPublic;
                privateKey = WebPushCrypto.decodePrivate(WebPushCrypto.unb64(configuredPrivate));
                return;
            }
            PushSettings saved = settings.findById(1L).orElse(null);
            if (saved == null) {
                KeyPair pair = WebPushCrypto.newKeyPair();
                saved = settings.save(new PushSettings(WebPushCrypto.b64(WebPushCrypto.encodePublic(pair.getPublic())),
                        WebPushCrypto.b64(WebPushCrypto.encodePrivate(pair.getPrivate()))));
                log.info("Se generaron las llaves VAPID de las notificaciones push (se guardaron en la base de datos).");
            }
            publicKey = saved.getPublicKey();
            privateKey = WebPushCrypto.decodePrivate(WebPushCrypto.unb64(saved.getPrivateKey()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudieron preparar las llaves de las notificaciones push.", exception);
        }
    }
}
