package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Vincular cuentas de Nexorix con cuentas del banco, y ver lo que llego por webhook. */
@Service
public class BankLinkService {

    public record LinkView(Long id, Long accountId, String accountName, String bank, String externalRef) {
    }

    public record EventView(Long id, String bank, String kind, String direction, String amount, String label,
                            String place, LocalDateTime occurredAt, String status, String risk, String riskReason) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankLinkRepository links;
    private final BankEventRepository events;
    private final AccountRepository accounts;
    private final UserRepository users;

    public BankLinkService(BankLinkRepository links, BankEventRepository events, AccountRepository accounts,
                           UserRepository users) {
        this.links = links;
        this.events = events;
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<LinkView> list(String username) {
        return links.findByUserIdOrderByIdAsc(user(username).getId()).stream()
                .map(l -> new LinkView(l.getId(), l.getAccount().getId(), l.getAccount().getName(), l.getBank(),
                        l.getExternalRef()))
                .toList();
    }

    /** En produccion externalRef lo da el agregador al conectar el banco; en la demo se genera si falta. */
    @Transactional
    public LinkView link(String username, Long accountId, String bank, String externalRef) {
        User user = user(username);
        Account account = accounts.findById(accountId == null ? -1L : accountId)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
        if (links.existsByAccountId(account.getId())) {
            throw new IllegalArgumentException("Esa cuenta ya está vinculada a un banco.");
        }
        String cleanBank = (bank == null || bank.isBlank() ? account.getBank() : bank).trim().toUpperCase(Locale.ROOT);
        if (cleanBank.length() > 40) {
            throw new IllegalArgumentException("El nombre del banco es demasiado largo.");
        }
        String ref = externalRef == null || externalRef.isBlank()
                ? "demo-%s-%06d".formatted(cleanBank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), RANDOM.nextInt(1_000_000))
                : externalRef.trim();
        if (ref.length() > 100 || links.findByExternalRef(ref).isPresent()) {
            throw new IllegalArgumentException("Esa referencia de cuenta no es válida o ya está en uso.");
        }
        BankLink saved = links.save(new BankLink(user, account, cleanBank, ref));
        return new LinkView(saved.getId(), account.getId(), account.getName(), cleanBank, ref);
    }

    @Transactional(readOnly = true)
    public List<EventView> recentEvents(String username) {
        return events.findByUserIdOrderByOccurredAtDescIdDesc(user(username).getId(), PageRequest.of(0, 50)).stream()
                .map(e -> new EventView(e.getId(), e.getBank(), e.getKind().name(), e.getDirection(),
                        e.getAmount().toPlainString(), e.getLabel(),
                        new com.nexorix.fraud.Geo(e.getCity(), e.getCountry(), 0, 0).label(),
                        e.getOccurredAt(), e.getStatus().name(), e.getRiskLevel(), e.getRiskReason()))
                .toList();
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
