package com.nexorix.split;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Amistad entre dos personas de Nexorix. Quien envia la solicitud es el
 * "requester"; la amistad solo cuenta cuando la otra persona la acepta.
 * Asi nadie puede agregar a otra a una cuenta compartida sin su permiso.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
public class Friendship {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Friendship() {
    }

    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void accept() {
        this.status = ACCEPTED;
    }

    public boolean isAccepted() {
        return ACCEPTED.equals(status);
    }

    /** La otra persona de la amistad. */
    public User other(Long myId) {
        return requester.getId().equals(myId) ? addressee : requester;
    }

    public Long getId() { return id; }
    public User getRequester() { return requester; }
    public User getAddressee() { return addressee; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
