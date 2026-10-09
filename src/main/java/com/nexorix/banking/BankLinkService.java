package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.user.User;
import com.nexorix.whatsapp.WhatsappLinkRepository;
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

    public record LinkView(Long id, Long accountId, String accountName, String bank, String externalRef,
                           String type, String balance) {
    }

    public record EventView(Long id, String bank, String kind, String direction, String amount, String label,
                            String place, LocalDateTime occurredAt, String status, String risk, String riskReason) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankLinkRepository links;
    private final BankEventRepository events;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final WhatsappLinkRepository whatsapp;
    private final AccountService accountService;
    private final BankBalanceProvider balances;

    public BankLinkService(BankLinkRepository links, BankEventRepository events, AccountRepository accounts,
                           UserRepository users, WhatsappLinkRepository whatsapp, AccountService accountService,
                           BankBalanceProvider balances) {
        this.accountService = accountService;
        this.balances = balances;
        this.links = links;
        this.events = events;
        this.accounts = accounts;
        this.users = users;
        this.whatsapp = whatsapp;
    }

    @Transactional(readOnly = true)
    public List<LinkView> list(String username) {
        return links.findByUserIdOrderByIdAsc(user(username).getId()).stream()
                .map(BankLinkService::view)
                .toList();
    }

    private static LinkView view(BankLink l) {
        return new LinkView(l.getId(), l.getAccount().getId(), l.getAccount().getName(), l.getBank(),
                l.getExternalRef(), l.getAccount().getType(), l.getAccount().getBalance().toPlainString());
    }

    /**
     * Vincula un banco o billetera nuevo: crea la cuenta en Nexorix y trae el saldo del banco.
     * La persona solo elige el banco; el saldo NUNCA se le pregunta.
     */
    @Transactional
    public LinkView connect(String username, String bank, String type, String name, String cardNumber,
                            String cardExpiry) {
        user(username);
        String cleanBank = bank == null ? "" : bank.trim();
        if (cleanBank.isEmpty()) {
            throw new IllegalArgumentException("Elige tu banco o billetera.");
        }
        if (cleanBank.length() > 40) {
            throw new IllegalArgumentException("El nombre del banco es demasiado largo.");
        }
        String cleanType = type == null || type.isBlank() ? "AHORROS" : type.trim().toUpperCase(Locale.ROOT);
        String baseName = name == null || name.isBlank() ? cleanBank : name.trim();

        String accountName = baseName;
        java.util.Set<String> taken = accountService.getAccountsByUsername(username).stream()
                .filter(a -> a.getBank().equalsIgnoreCase(cleanBank))
                .map(a -> a.getName().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        for (int i = 2; taken.contains(accountName.toLowerCase(Locale.ROOT)); i++) {
            accountName = baseName + " " + i;
        }

        String upperBank = cleanBank.toUpperCase(Locale.ROOT);
        String ref = "demo-%s-%06d".formatted(upperBank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""),
                RANDOM.nextInt(1_000_000));
        while (links.findByExternalRef(ref).isPresent()) {
            ref = ref + "x";
        }
        java.math.BigDecimal balance = balances.currentBalance(upperBank, ref);
        // En una tarjeta de credito el "saldo" que trae el banco es el cupo disponible (el cupo total se asume igual).
        boolean card = cardNumber != null && !cardNumber.isBlank();
        Account account = card
                ? accountService.createAccount(accountName, cleanType, cleanBank, balance, username,
                        cleanType.equals("CREDITO") ? balance : null, cardNumber, cardExpiry)
                : accountService.createAccount(accountName, cleanType, cleanBank, balance, username);
        return view(links.save(new BankLink(user(username), account, upperBank, ref)));
    }

    /** En produccion externalRef lo da el agregador al conectar el banco; en la demo se genera si falta. */
    @Transactional
    public LinkView link(String username, Long accountId, String bank, String externalRef) {
        return link(username, accountId, bank, externalRef, null);
    }

    /**
     * Vincular una cuenta REAL (viene con la referencia del agregador) exige que el celular
     * registrado en el banco sea el de la misma persona: el de WhatsApp verificado o el de seguridad.
     * Sin referencia (demo) no se pide.
     */
    @Transactional
    public LinkView link(String username, Long accountId, String bank, String externalRef, String bankPhone) {
        User user = user(username);
        if (externalRef != null && !externalRef.isBlank()) {
            verifySamePerson(user, bankPhone);
        }
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
        return view(links.save(new BankLink(user, account, cleanBank, ref)));
    }

    private void verifySamePerson(User user, String bankPhone) {
        if (bankPhone == null || bankPhone.isBlank()) {
            throw new IllegalArgumentException("Escribe el celular registrado en tu banco para verificar que la cuenta es tuya.");
        }
        List<String> mine = new java.util.ArrayList<>();
        if (user.getSecurityPhone() != null) mine.add(user.getSecurityPhone());
        whatsapp.findByUserId(user.getId()).filter(w -> w.isVerified()).ifPresent(w -> mine.add(w.getPhone()));
        if (mine.isEmpty()) {
            throw new IllegalArgumentException(
                    "Primero registra y verifica tu celular (Seguridad o Compras) para vincular un banco real.");
        }
        if (mine.stream().noneMatch(phone -> PhoneMatcher.same(phone, bankPhone))) {
            throw new IllegalArgumentException(
                    "El celular del banco no coincide con el tuyo. Solo puedes vincular cuentas que sean tuyas.");
        }
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
