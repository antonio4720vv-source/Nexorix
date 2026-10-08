package com.nexorix.controller;

import com.nexorix.account.Account;
import com.nexorix.account.AccountService;
import com.nexorix.dto.AccountResponse;
import com.nexorix.dto.DashboardResponse;
import com.nexorix.dto.TransactionResponse;
import com.nexorix.trace.MoneyFlowService;
import com.nexorix.trace.MoneyFlowSummary;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final MoneyFlowService moneyFlowService;

    public DashboardController(
            AccountService accountService,
            TransactionService transactionService,
            MoneyFlowService moneyFlowService
    ) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.moneyFlowService = moneyFlowService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        // Cuentas del usuario autenticado.
        List<Account> accounts =
                accountService.getAccountsByUsername(username);

        // Dinero total: las transferencias internas no lo cambian,
        // porque el dinero solo paso de una cuenta a otra.
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AccountResponse> accountResponses =
                accounts.stream()
                        .map(AccountResponse::fromAccount)
                        .toList();

        // Movimientos del usuario autenticado.
        List<Transaction> transactions =
                transactionService
                        .getRecentTransactionsByUsername(username);

        List<TransactionResponse> transactionResponses =
                transactions.stream()
                        .map(TransactionResponse::fromTransaction)
                        .toList();

        // Flujo real de dinero calculado con Trace.
        MoneyFlowSummary moneyFlow =
                moneyFlowService.summarize(username);

        return ResponseEntity.ok(
                new DashboardResponse(
                        totalBalance,
                        moneyFlow,
                        accountResponses,
                        transactionResponses,
                        moneyFlowService.internalPortions(username)
                )
        );
    }
}
