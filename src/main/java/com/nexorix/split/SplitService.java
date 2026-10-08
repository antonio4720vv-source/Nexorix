package com.nexorix.split;

import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dividir gastos: una persona pago la cuenta y la reparte en partes iguales
 * entre ella y sus amigos. Cada amigo recibe su cobro (en su Nexorix y con un
 * enlace) y el pagador va marcando quien ya le reembolso.
 *
 * Nexorix no mueve dinero: el reembolso se hace por fuera (Nequi, efectivo...) y aqui solo se
 * lleva la cuenta.
 */
@Service
public class SplitService {

    public static final int MAX_PARTICIPANTS = 20;
    private static final int LIST_LIMIT = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SplitExpenseRepository expenseRepository;
    private final SplitShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;
    private final String publicUrl;

    public SplitService(
            SplitExpenseRepository expenseRepository,
            SplitShareRepository shareRepository,
            UserRepository userRepository,
            FriendService friendService,
            @Value("${nexorix.public-url:http://localhost:8080}") String publicUrl
    ) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
    }

    // ============================================================
    // LA CUENTA
    // ============================================================

    /** Lo que le toca a cada quien. Los amigos pagan todos lo mismo; el pagador absorbe los centavos sobrantes. */
    public record Split(BigDecimal each, BigDecimal payerShare) {
    }

    /**
     * Divide el total en partes iguales entre el pagador y sus amigos (people = amigos + 1).
     * Se calcula en centavos para no perder ni inventar plata: each * amigos + payerShare == total.
     */
    public static Split divide(BigDecimal total, int friends) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        }
        if (friends < 1) {
            throw new IllegalArgumentException("Elige al menos un amigo para dividir la cuenta.");
        }
        long cents = total.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        long people = friends + 1L;
        long each = cents / people;
        if (each < 1) {
            throw new IllegalArgumentException("El monto es muy pequeño para dividirlo entre tantas personas.");
        }
        long payer = cents - each * friends;
        return new Split(BigDecimal.valueOf(each, 2), BigDecimal.valueOf(payer, 2));
    }

    // ============================================================
    // VISTAS
    // ============================================================

    public record ShareView(Long id, String username, String name, String amount, String status,
                            String reportedAt, String settledAt, String link) {
    }

    /** Una cuenta que yo pague, con lo que cada amigo me debe. */
    public record ExpenseView(Long id, String title, String total, String payerShare, String status,
                              String createdAt, List<ShareView> shares, String pending) {
    }

    /** Algo que yo le debo a otra persona. */
    public record DebtView(Long shareId, String title, String payerUsername, String payerName, String amount,
                           String status, String createdAt, String link) {
    }

    public record Overview(List<ExpenseView> owedToMe, List<DebtView> iOwe) {
    }

    /** Lo que se ve al abrir un enlace de cobro. */
    public record CollectView(Long shareId, String title, String total, String amount, String status,
                              String payerUsername, String payerName, String participantUsername,
                              String participantName, boolean iAmPayer, boolean cancelled) {
    }

    // ============================================================
    // CREAR
    // ============================================================

    @Transactional
    public ExpenseView create(String username, String title, BigDecimal total, List<String> usernames) {
        User payer = user(username);

        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto total debe ser mayor que cero.");
        }
        if (total.compareTo(TransactionService.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El monto es demasiado alto.");
        }
        String cleanTitle = title == null || title.isBlank() ? "Cuenta compartida" : title.trim().replaceAll("\\s+", " ");
        if (cleanTitle.length() > 100) {
            throw new IllegalArgumentException("El nombre de la cuenta tiene máximo 100 caracteres.");
        }

        Set<String> names = new LinkedHashSet<>();
        if (usernames != null) {
            usernames.stream().filter(n -> n != null && !n.isBlank()).map(String::trim).forEach(names::add);
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un amigo para dividir la cuenta.");
        }
        if (names.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Puedes dividir entre máximo " + MAX_PARTICIPANTS + " amigos.");
        }
        if (names.contains(payer.getUsername())) {
            throw new IllegalArgumentException("Tú ya cuentas como participante: no te elijas a ti mismo.");
        }

        List<User> candidates = new ArrayList<>();
        for (String name : names) {
            candidates.add(userRepository.findByUsername(name).filter(User::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("No encontramos al usuario " + name + ".")));
        }
        // Solo se le cobra a amigos que aceptaron: nadie recibe un cobro de un desconocido.
        List<User> friends = friendService.acceptedFriends(payer, candidates);
        if (friends.size() != candidates.size()) {
            String notFriends = candidates.stream().filter(c -> !friends.contains(c)).map(User::getUsername)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Solo puedes dividir con tus amigos. Aún no son amigos: " + notFriends + ".");
        }

        Split split = divide(total, friends.size());
        SplitExpense expense = expenseRepository.save(new SplitExpense(
                payer, cleanTitle, total.setScale(2, RoundingMode.HALF_UP), split.payerShare()));

        List<SplitShare> shares = new ArrayList<>();
        for (User friend : friends) {
            shares.add(new SplitShare(expense, friend, split.each(), newToken()));
        }
        shareRepository.saveAll(shares);
        return toExpenseView(expense, shares);
    }

    // ============================================================
    // CONSULTAR
    // ============================================================

    @Transactional(readOnly = true)
    public Overview overview(String username) {
        User me = user(username);

        List<SplitExpense> expenses = expenseRepository.findByPayerIdOrderByIdDesc(me.getId(), PageRequest.of(0, LIST_LIMIT));
        Map<Long, List<SplitShare>> byExpense = new LinkedHashMap<>();
        if (!expenses.isEmpty()) {
            for (SplitShare share : shareRepository.findByExpenseIds(expenses.stream().map(SplitExpense::getId).toList())) {
                byExpense.computeIfAbsent(share.getExpense().getId(), k -> new ArrayList<>()).add(share);
            }
        }
        List<ExpenseView> owedToMe = expenses.stream()
                .map(e -> toExpenseView(e, byExpense.getOrDefault(e.getId(), List.of())))
                .toList();

        List<DebtView> iOwe = shareRepository.findOwedBy(me.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(s -> new DebtView(s.getId(), s.getExpense().getTitle(), s.getExpense().getPayer().getUsername(),
                        s.getExpense().getPayer().getName(), s.getAmount().toPlainString(), s.getStatus(),
                        s.getExpense().getCreatedAt().toString(), link(s)))
                .toList();

        return new Overview(owedToMe, iOwe);
    }

    /** Abrir un enlace de cobro: solo lo ven el pagador y la persona a quien se le cobra. */
    @Transactional(readOnly = true)
    public CollectView collect(String username, String token) {
        User me = user(username);
        SplitShare share = visibleShare(me, token);
        SplitExpense expense = share.getExpense();
        return new CollectView(share.getId(), expense.getTitle(), expense.getTotalAmount().toPlainString(),
                share.getAmount().toPlainString(), share.getStatus(),
                expense.getPayer().getUsername(), expense.getPayer().getName(),
                share.getParticipant().getUsername(), share.getParticipant().getName(),
                expense.getPayer().getId().equals(me.getId()), expense.isCancelled());
    }

    // ============================================================
    // ACCIONES
    // ============================================================

    /** "Ya pagué": solo la persona a quien se le cobra. El pagador despues lo confirma. */
    @Transactional
    public CollectView reportPaid(String username, String token) {
        User me = user(username);
        SplitShare share = visibleShare(me, token);
        if (!share.getParticipant().getId().equals(me.getId())) {
            throw new IllegalArgumentException("Solo quien debe puede avisar que ya pagó.");
        }
        if (share.getExpense().isCancelled()) {
            throw new IllegalArgumentException("Esta cuenta fue cancelada.");
        }
        share.reportPaid();
        shareRepository.save(share);
        return collect(username, token);
    }

    /** "Ya me pagó": solo el pagador. */
    @Transactional
    public ShareView settle(String username, Long shareId) {
        User me = user(username);
        SplitShare share = shareRepository.findWithExpense(shareId)
                .filter(s -> s.getExpense().getPayer().getId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobro no encontrado."));
        if (share.getExpense().isCancelled()) {
            throw new IllegalArgumentException("Esta cuenta fue cancelada.");
        }
        share.settle();
        return toShareView(shareRepository.save(share));
    }

    @Transactional
    public void cancel(String username, Long expenseId) {
        User me = user(username);
        SplitExpense expense = expenseRepository.findByIdAndPayerId(expenseId, me.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada."));
        expense.cancel();
        expenseRepository.save(expense);
    }

    // ============================================================
    // AYUDAS
    // ============================================================

    private SplitShare visibleShare(User me, String token) {
        // Mismo error si no existe o si no es tuyo: el enlace no revela a quien pertenece.
        Optional<SplitShare> found = token == null || token.length() > 64 ? Optional.empty() : shareRepository.findByToken(token);
        return found
                .filter(s -> s.getParticipant().getId().equals(me.getId())
                        || s.getExpense().getPayer().getId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Este cobro no existe o no es para ti."));
    }

    private ExpenseView toExpenseView(SplitExpense expense, List<SplitShare> shares) {
        BigDecimal pending = shares.stream().filter(s -> !SplitShare.SETTLED.equals(s.getStatus()))
                .map(SplitShare::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExpenseView(expense.getId(), expense.getTitle(), expense.getTotalAmount().toPlainString(),
                expense.getPayerShare().toPlainString(), expense.getStatus(), expense.getCreatedAt().toString(),
                shares.stream().map(this::toShareView).toList(),
                expense.isCancelled() ? "0" : pending.toPlainString());
    }

    private ShareView toShareView(SplitShare share) {
        return new ShareView(share.getId(), share.getParticipant().getUsername(), share.getParticipant().getName(),
                share.getAmount().toPlainString(), share.getStatus(),
                share.getReportedAt() == null ? null : share.getReportedAt().toString(),
                share.getSettledAt() == null ? null : share.getSettledAt().toString(), link(share));
    }

    String link(SplitShare share) {
        return publicUrl + "/cobro.html?t=" + share.getToken();
    }

    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
