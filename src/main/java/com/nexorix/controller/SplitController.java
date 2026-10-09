package com.nexorix.controller;

import com.nexorix.split.DemoSplitService;
import com.nexorix.split.SplitService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Dividir gastos entre amigos y cobrar la parte de cada uno. */
@RestController
@RequestMapping("/api/split")
public class SplitController {

    private final SplitService service;
    private final DemoSplitService demo;

    public SplitController(SplitService service, DemoSplitService demo) {
        this.service = service;
        this.demo = demo;
    }

    public record CreateRequest(String title, BigDecimal total, List<String> participants) {
    }

    @GetMapping
    public SplitService.Overview overview() {
        return service.overview(FriendController.username());
    }

    @PostMapping
    public SplitService.ExpenseView create(@RequestBody CreateRequest body) {
        return service.create(FriendController.username(), body.title(), body.total(), body.participants());
    }

    /** Demo: amigos de ejemplo y una cena dividida entre los tres. */
    @PostMapping("/demo")
    public SplitService.ExpenseView demo() {
        return demo.run(FriendController.username());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable Long id) {
        service.cancel(FriendController.username(), id);
        return Map.of("ok", true);
    }

    /** El pagador confirma que ya le reembolsaron esta parte. */
    @PostMapping("/shares/{id}/settle")
    public SplitService.ShareView settle(@PathVariable Long id) {
        return service.settle(FriendController.username(), id);
    }

    /** Lo que se ve al abrir un enlace de cobro (/cobro.html?t=...). */
    @GetMapping("/collect/{token}")
    public SplitService.CollectView collect(@PathVariable String token) {
        return service.collect(FriendController.username(), token);
    }

    /** "Ya pagué". */
    @PostMapping("/collect/{token}/paid")
    public SplitService.CollectView paid(@PathVariable String token) {
        return service.reportPaid(FriendController.username(), token);
    }
}
