package com.nexorix.dto;

import com.nexorix.importer.TransactionClassifier;
import com.nexorix.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {

    private final Long id;
    private final BigDecimal amount;
    private final String type;
    private final String description;
    private final LocalDateTime transactionDate;
    private final String reference;
    private final Long accountId;
    private final String accountName;
    private final String bank;
    private final String category;
    private final String categoryLabel;
    private final String source;

    public TransactionResponse(
            Long id,
            BigDecimal amount,
            String type,
            String description,
            LocalDateTime transactionDate,
            String reference,
            Long accountId,
            String accountName,
            String bank,
            String category,
            String source
    ) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.transactionDate = transactionDate;
        this.reference = reference;
        this.accountId = accountId;
        this.accountName = accountName;
        this.bank = bank;
        this.category = category;
        this.categoryLabel = TransactionClassifier.label(category);
        this.source = source == null ? "MANUAL" : source;
    }

    public static TransactionResponse fromTransaction(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getReference(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getAccount().getBank(),
                transaction.getCategory(),
                transaction.getSource()
        );
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public String getReference() {
        return reference;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getBank() {
        return bank;
    }

    public String getCategory() {
        return category;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public String getSource() {
        return source;
    }
}
