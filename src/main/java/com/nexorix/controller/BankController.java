package com.nexorix.controller;

import com.nexorix.banking.BankDemoService;
import com.nexorix.banking.BankLinkService;
import com.nexorix.banking.BankSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Cuentas vinculadas, movimientos del banco, desbloqueo y simulador de la demo. */
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final BankLinkService linkService;
    private final BankSyncService syncService;
    private final BankDemoService demoService;

    public BankController(BankLinkService linkService, BankSyncService syncService, BankDemoService demoService) {
        this.linkService = linkService;
        this.syncService = syncService;
        this.demoService = demoService;
    }

    public record LinkRequest(Long accountId, String bank, String externalRef) {
    }

    @GetMapping("/links")
    public List<BankLinkService.LinkView> links() {
        return linkService.list(username());
    }

    @PostMapping("/links")
    public BankLinkService.LinkView link(@RequestBody LinkRequest request) {
        return linkService.link(username(), request.accountId(), request.bank(), request.externalRef());
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

    @PostMapping("/demo/{scenario}")
    public List<BankSyncService.Result> simulate(@PathVariable String scenario) {
        return demoService.simulate(username(), scenario);
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
