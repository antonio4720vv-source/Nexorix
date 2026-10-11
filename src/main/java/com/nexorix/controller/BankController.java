package com.nexorix.controller;

import com.nexorix.banking.BankLinkService;
import com.nexorix.banking.BankSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Cuentas vinculadas, movimientos del banco y desbloqueo. */
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final BankLinkService linkService;
    private final BankSyncService syncService;

    public BankController(BankLinkService linkService, BankSyncService syncService) {
        this.linkService = linkService;
        this.syncService = syncService;
    }

    public record LinkRequest(Long accountId, String bank, String externalRef, String phone) {
    }

    @GetMapping("/links")
    public List<BankLinkService.LinkView> links() {
        return linkService.list(username());
    }

    @PostMapping("/links")
    public BankLinkService.LinkView link(@RequestBody LinkRequest request) {
        return linkService.link(username(), request.accountId(), request.bank(), request.externalRef(), request.phone());
    }

    @GetMapping("/events")
    public List<BankLinkService.EventView> events() {
        return linkService.recentEvents(username());
    }

    /** "Fui yo": aplica un movimiento que se habia bloqueado. */
    @PostMapping("/events/{id}/release")
    public BankSyncService.Result release(@PathVariable Long id) {
        return syncService.release(username(), id);
    }

    static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        return authentication.getName();
    }
}
