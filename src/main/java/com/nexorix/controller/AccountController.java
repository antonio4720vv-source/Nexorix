package com.nexorix.controller;

import com.nexorix.account.Account;
import com.nexorix.account.AccountService;
import com.nexorix.dto.AccountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam String bank,
            @RequestParam BigDecimal balance
    ) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        Account account = accountService.createAccount(
                name,
                type,
                bank,
                balance,
                username
        );

        return ResponseEntity.ok(
                AccountResponse.fromAccount(account)
        );
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        List<AccountResponse> accounts =
                accountService.getAccountsByUsername(username)
                        .stream()
                        .map(AccountResponse::fromAccount)
                        .toList();

        return ResponseEntity.ok(accounts);
    }
}