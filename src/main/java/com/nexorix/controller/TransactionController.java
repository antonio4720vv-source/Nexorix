package com.nexorix.controller;

import com.nexorix.dto.TransactionResponse;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** Registrar un ingreso o un egreso en una cuenta propia. */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @RequestParam BigDecimal amount,
            @RequestParam String type,
            @RequestParam String description,
            @RequestParam Long accountId,
            @RequestParam(required = false) String reference
    ) {
        String username = currentUsername();

        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        Transaction transaction = transactionService.createTransaction(
                amount, type, description, LocalDateTime.now(), accountId, reference, username);

        return ResponseEntity.ok(TransactionResponse.fromTransaction(transaction));
    }

    /** Registrar una transferencia entre dos cuentas propias (salida + entrada). */
    @PostMapping("/transfer")
    public ResponseEntity<List<TransactionResponse>> transfer(
            @RequestParam Long fromAccountId,
            @RequestParam Long toAccountId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description
    ) {
        String username = currentUsername();

        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        List<TransactionResponse> created = transactionService
                .registerTransfer(fromAccountId, toAccountId, amount, description, username)
                .stream()
                .map(TransactionResponse::fromTransaction)
                .toList();

        return ResponseEntity.ok(created);
    }

    private String currentUsername() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        return authentication.getName();
    }
}
