package com.nexorix.dto;

import com.nexorix.trace.MoneyFlowSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DashboardResponse {

    private final BigDecimal totalBalance;
    private final MoneyFlowSummary moneyFlow;
    private final List<AccountResponse> accounts;
    private final List<TransactionResponse> recentTransactions;
    /** id de movimiento -> parte que fue transferencia interna (Trace). */
    private final Map<Long, BigDecimal> internalPortions;

    public DashboardResponse(
            BigDecimal totalBalance,
            MoneyFlowSummary moneyFlow,
            List<AccountResponse> accounts,
            List<TransactionResponse> recentTransactions,
            Map<Long, BigDecimal> internalPortions
    ) {
        this.totalBalance = totalBalance;
        this.moneyFlow = moneyFlow;
        this.accounts = accounts;
        this.recentTransactions = recentTransactions;
        this.internalPortions = internalPortions;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public MoneyFlowSummary getMoneyFlow() {
        return moneyFlow;
    }

    public List<AccountResponse> getAccounts() {
        return accounts;
    }

    public List<TransactionResponse> getRecentTransactions() {
        return recentTransactions;
    }

    public Map<Long, BigDecimal> getInternalPortions() {
        return internalPortions;
    }
}
