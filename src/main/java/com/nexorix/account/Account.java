package com.nexorix.account;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 100)
    private String bank;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** Solo tarjetas de credito: el cupo total. En CREDITO, "balance" es el cupo disponible. */
    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    /** Ultimos 4 digitos de la tarjeta (debito o credito), si la persona los puso. */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Account() {
    }

    public Account(String name, String type, String bank, BigDecimal balance, User user) {
        this.name = name;
        this.type = type;
        this.bank = bank;
        this.balance = balance;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public boolean isCredit() {
        return "CREDITO".equals(type);
    }

    /**
     * Aplica un cambio de saldo sin dejarlo nunca por debajo de cero (ni, en credito, por encima
     * del cupo). Devuelve el saldo resultante.
     */
    public BigDecimal applyClamped(BigDecimal delta) {
        BigDecimal next = balance.add(delta).max(BigDecimal.ZERO);
        if (isCredit() && creditLimit != null) {
            next = next.min(creditLimit);
        }
        balance = next;
        return next;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}