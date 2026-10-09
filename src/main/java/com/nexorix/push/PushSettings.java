package com.nexorix.push;

import jakarta.persistence.*;

/** Llaves VAPID generadas por Nexorix cuando no se configuran por variable de entorno (una sola fila, id = 1). */
@Entity
@Table(name = "push_settings")
public class PushSettings {

    @Id
    private Long id = 1L;

    @Column(name = "public_key", nullable = false, length = 120)
    private String publicKey;

    @Column(name = "private_key", nullable = false, length = 120)
    private String privateKey;

    protected PushSettings() {
    }

    public PushSettings(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
