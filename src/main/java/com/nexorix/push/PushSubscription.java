package com.nexorix.push;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Un navegador o celular que acepto recibir avisos de Nexorix (Web Push). */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_user", columnList = "user_id"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 600)
    private String endpoint;

    @Column(nullable = false, length = 150)
    private String p256dh;

    @Column(nullable = false, length = 60)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PushSubscription() {
    }

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    /** El mismo navegador se vuelve a suscribir con otras llaves, o con otra cuenta. */
    public void update(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
