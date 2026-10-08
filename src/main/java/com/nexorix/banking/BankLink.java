package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Une una cuenta de Nexorix con la cuenta real del banco (la referencia que manda el agregador). */
@Entity
@Table(name = "bank_links")
public class BankLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(nullable = false, length = 40)
    private String bank;

    @Column(name = "external_ref", nullable = false, unique = true, length = 100)
    private String externalRef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected BankLink() {
    }

    public BankLink(User user, Account account, String bank, String externalRef) {
        this.user = user;
        this.account = account;
        this.bank = bank;
        this.externalRef = externalRef;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public String getBank() {
        return bank;
    }

    public String getExternalRef() {
        return externalRef;
    }
}
