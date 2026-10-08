package com.nexorix.dto;

import com.nexorix.account.Account;

import java.math.BigDecimal;

public class AccountResponse {

    private final Long id;
    private final String name;
    private final String type;
    private final String bank;
    private final BigDecimal balance;

    public AccountResponse(
            Long id,
            String name,
            String type,
            String bank,
            BigDecimal balance
    ) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.bank = bank;
        this.balance = balance;
    }

    public static AccountResponse fromAccount(Account account) {

        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getBank(),
                account.getBalance()
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
}