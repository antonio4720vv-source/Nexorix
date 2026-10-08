package com.nexorix.trace;

import com.nexorix.transaction.Transaction;

import java.math.BigDecimal;

public class TransferMatch {

    private final Transaction originTransaction;
    private final Transaction destinationTransaction;
    private final BigDecimal amount;
    private final int score;
    private final String classification;

    public TransferMatch(
            Transaction originTransaction,
            Transaction destinationTransaction,
            BigDecimal amount,
            int score,
            String classification
    ) {
        this.originTransaction = originTransaction;
        this.destinationTransaction = destinationTransaction;
        this.amount = amount;
        this.score = score;
        this.classification = classification;
    }

    public Transaction getOriginTransaction() {
        return originTransaction;
    }

    public Transaction getDestinationTransaction() {
        return destinationTransaction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getScore() {
        return score;
    }

    public String getClassification() {
        return classification;
    }
}