package com.nexorix.dto;

import com.nexorix.trace.ReconciliationMatch;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReconciliationMatchResponse {

    private final Long id;

    private final Long originTransactionId;
    private final Long destinationTransactionId;

    private final BigDecimal matchedAmount;

    private final int score;

    private final String status;

    private final String classification;

    private final LocalDateTime createdAt;

    public ReconciliationMatchResponse(
            Long id,
            Long originTransactionId,
            Long destinationTransactionId,
            BigDecimal matchedAmount,
            int score,
            String status,
            String classification,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.originTransactionId = originTransactionId;
        this.destinationTransactionId = destinationTransactionId;
        this.matchedAmount = matchedAmount;
        this.score = score;
        this.status = status;
        this.classification = classification;
        this.createdAt = createdAt;
    }

    public static ReconciliationMatchResponse fromMatch(
            ReconciliationMatch match
    ) {

        return new ReconciliationMatchResponse(
                match.getId(),
                match.getOriginTransaction().getId(),
                match.getDestinationTransaction().getId(),
                match.getMatchedAmount(),
                match.getScore(),
                match.getStatus().name(),
                match.getClassification(),
                match.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getOriginTransactionId() {
        return originTransactionId;
    }

    public Long getDestinationTransactionId() {
        return destinationTransactionId;
    }

    public BigDecimal getMatchedAmount() {
        return matchedAmount;
    }

    public int getScore() {
        return score;
    }

    public String getStatus() {
        return status;
    }

    public String getClassification() {
        return classification;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}