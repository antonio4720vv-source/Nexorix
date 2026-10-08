package com.nexorix.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TraceMatchResponse {

    private final Long originTransactionId;
    private final String originBank;
    private final String originAccountName;
    private final BigDecimal originAmount;
    private final LocalDateTime originDate;

    private final Long destinationTransactionId;
    private final String destinationBank;
    private final String destinationAccountName;
    private final BigDecimal destinationAmount;
    private final LocalDateTime destinationDate;

    private final BigDecimal amount;
    private final int score;
    private final String classification;

    public TraceMatchResponse(
            Long originTransactionId,
            String originBank,
            String originAccountName,
            BigDecimal originAmount,
            LocalDateTime originDate,
            Long destinationTransactionId,
            String destinationBank,
            String destinationAccountName,
            BigDecimal destinationAmount,
            LocalDateTime destinationDate,
            BigDecimal amount,
            int score,
            String classification
    ) {
        this.originTransactionId = originTransactionId;
        this.originBank = originBank;
        this.originAccountName = originAccountName;
        this.originAmount = originAmount;
        this.originDate = originDate;
        this.destinationTransactionId = destinationTransactionId;
        this.destinationBank = destinationBank;
        this.destinationAccountName = destinationAccountName;
        this.destinationAmount = destinationAmount;
        this.destinationDate = destinationDate;
        this.amount = amount;
        this.score = score;
        this.classification = classification;
    }

    public Long getOriginTransactionId() {
        return originTransactionId;
    }

    public String getOriginBank() {
        return originBank;
    }

    public String getOriginAccountName() {
        return originAccountName;
    }

    public BigDecimal getOriginAmount() {
        return originAmount;
    }

    public LocalDateTime getOriginDate() {
        return originDate;
    }

    public Long getDestinationTransactionId() {
        return destinationTransactionId;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public BigDecimal getDestinationAmount() {
        return destinationAmount;
    }

    public LocalDateTime getDestinationDate() {
        return destinationDate;
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