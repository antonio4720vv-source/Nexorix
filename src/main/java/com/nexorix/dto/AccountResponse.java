package com.nexorix.dto;

import com.nexorix.account.Account;

import java.math.BigDecimal;

public class AccountResponse {

    private final Long id;
    private final String name;
    private final String type;
    private final String bank;
    private final BigDecimal balance;
    private final BigDecimal creditLimit;
    private final String cardLast4;

    public AccountResponse(
            Long id,
            String name,
            String type,
            String bank,
            BigDecimal balance,
            BigDecimal creditLimit,
            String cardLast4
    ) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.bank = bank;
        this.balance = balance;
        this.creditLimit = creditLimit;
        this.cardLast4 = cardLast4;
    }

    public static AccountResponse fromAccount(Account account) {

        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getBank(),
                account.getBalance(),
                account.getCreditLimit(),
                account.getCardLast4()
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getBank() {
        return bank;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public String getCardLast4() {
        return cardLast4;
    }
}
