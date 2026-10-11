package com.nexorix.notification;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.ai.RateLimiter;
import com.nexorix.transaction.TransactionService;
import com.nexorix.banking.BankLink;
import com.nexorix.banking.BankLinkService;
import com.nexorix.banking.BankSyncService;
import com.nexorix.banking.BankWebhookPayload;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Movimientos que llegan desde las notificaciones del celular, para quien
 * prefiere no escribir sus cuentas a mano.
 *
 * El navegador no puede leer las notificaciones de otras apps (ni en Android
 * ni en iPhone): lo hace la app acompañante de Android, que envía aquí solo las
 * de bancos. Nexorix no guarda el texto completo, solo el movimiento entendido.
 */
@Service
public class NotificationService {

    public static final String TOKEN_PREFIX = "nxr_";
    private static final int MAX_TEXT = 1000;

    /** Resultado de recibir un aviso. */
    public enum Outcome { REGISTERED, DUPLICATE, IGNORED }

    private final NotificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final BankSyncService sync;
    private final BankLinkService linkService;
    private final SecureRandom random = new SecureRandom();
    private final RateLimiter limiter = new RateLimiter(120, Duration.ofMinutes(1));

    public NotificationService(NotificationTokenRepository tokens, UserRepository userRepository,
                               AccountRepository accountRepository, AccountService accountService,
                               BankSyncService sync, BankLinkService linkService) {
        this.tokens = tokens;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.sync = sync;
        this.linkService = linkService;
    }

    /** Crea (o cambia) la llave de la persona. La llave anterior deja de servir. */
    @Transactional
    public String createToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);

        NotificationToken existing = tokens.findByUserUsername(username).orElse(null);
        if (existing == null) {
            tokens.save(new NotificationToken(user, hash));
        } else {
            existing.replaceWith(hash);
            tokens.save(existing);
        }
        return token;
    }

    @Transactional
    public void revoke(String username) {
        tokens.findByUserUsername(username).ifPresent(tokens::delete);
    }

    public Optional<NotificationToken> status(String username) {
        return tokens.findByUserUsername(username);
    }

    /**
     * Recibe un aviso del celular. Lanza SecurityException si la llave no sirve
     * y IllegalStateException si se envían demasiados avisos seguidos.
     */
    @Transactional
    public Outcome ingest(String token, String packageName, String title, String text, Long postedAtMillis) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            throw new SecurityException("Llave no válida.");
        }
        String tokenHash = hash(token);
        NotificationToken saved = tokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new SecurityException("Llave no válida."));
        if (!limiter.tryAcquire(tokenHash)) {
            throw new IllegalStateException("Demasiados avisos seguidos. Intenta en un minuto.");
        }

        User user = saved.getUser();
        String cleanText = cut(text);
        String cleanTitle = cut(title);
        String cleanPackage = packageName == null ? "" : packageName.trim();

        Optional<ParsedNotification> parsed = NotificationParser.parse(cleanPackage, cleanTitle, cleanText);
        if (parsed.isEmpty()) {
            saved.markUsed(false);
            return Outcome.IGNORED;
        }
        ParsedNotification movement = parsed.get();

        OffsetDateTime when = whenOf(postedAtMillis);
        // Mismo aviso (mismo texto y hora) = mismo evento: no se registra dos veces.
        String eventId = "ntf-" + hash(user.getId() + "|" + cleanPackage + "|" + postedAtMillis + "|"
                + cleanTitle + "|" + cleanText).substring(0, 32);

        Account account = accountFor(user, movement);
        BankLink link = linkService.linkFor(user, account);

        boolean debit = movement.type().equals("EGRESO");
        String channel = movement.purchase() ? "POS" : "TRANSFER";
        BankWebhookPayload.Counterparty other = !movement.purchase() && movement.label() != null
                ? new BankWebhookPayload.Counterparty(movement.label(), null, null) : null;
        BankSyncService.Result result = sync.process(new BankWebhookPayload(eventId, movement.bank().name(),
                link.getExternalRef(), debit ? "DEBIT" : "CREDIT", movement.amount(), "COP", channel,
                movement.purchase() ? movement.label() : null, other, null, when));

        if (result.status().equals("DUPLICATE")) {
            saved.markUsed(false);
            return Outcome.DUPLICATE;
        }
        // Si el aviso dice el saldo, ese es el saldo real de la cuenta.
        if (movement.balance() != null && result.status().equals("APPLIED")
                && movement.balance().compareTo(TransactionService.MAX_AMOUNT) <= 0) {
            account.setBalance(movement.balance().setScale(2, java.math.RoundingMode.HALF_UP));
            accountRepository.save(account);
        }
        saved.markUsed(true);
        return Outcome.REGISTERED;
    }

    /** La cuenta de ese banco; si la persona no la agregó, se crea sola (saldo en cero). */
    private Account accountFor(User user, ParsedNotification movement) {
        List<Account> mine = accountRepository.findByUserUsername(user.getUsername());
        return mine.stream()
                .filter(a -> a.getBank().equalsIgnoreCase(movement.bank().name()))
                .findFirst()
                .orElseGet(() -> accountService.createAccount(
                        movement.bank().name(),
                        movement.bank().wallet() ? "BILLETERA" : "AHORROS",
                        movement.bank().name(),
                        BigDecimal.ZERO,
                        user.getUsername()));
    }

    private static OffsetDateTime whenOf(Long millis) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (millis == null || millis <= 0) {
            return now;
        }
        OffsetDateTime posted = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        // Fechas del futuro o muy viejas: se usa la hora de llegada.
        return posted.isAfter(now.plusMinutes(5)) || posted.isBefore(now.minusDays(7)) ? now : posted;
    }

    private static String cut(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
