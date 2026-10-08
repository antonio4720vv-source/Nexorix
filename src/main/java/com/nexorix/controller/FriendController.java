package com.nexorix.controller;

import com.nexorix.split.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Amigos para dividir gastos. */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService service;

    public FriendController(FriendService service) {
        this.service = service;
    }

    public record UsernameRequest(String username) {
    }

    @GetMapping
    public FriendService.Friends list() {
        return service.list(username());
    }

    /** Buscador por nombre de usuario (minimo 3 letras). */
    @GetMapping("/search")
    public List<FriendService.SearchResult> search(@RequestParam("q") String query) {
        return service.search(username(), query);
    }

    @PostMapping("/requests")
    public FriendService.Friends request(@RequestBody UsernameRequest body) {
        return service.request(username(), body == null ? null : body.username());
    }

    @PostMapping("/requests/{username}/accept")
    public FriendService.Friends accept(@PathVariable("username") String other) {
        return service.accept(username(), other);
    }

    /** Quitar un amigo, rechazar una solicitud o cancelar la que enviaste. */
    @DeleteMapping("/{username}")
    public FriendService.Friends remove(@PathVariable("username") String other) {
        return service.remove(username(), other);
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
