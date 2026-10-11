package com.nexorix.notification;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.ai.RateLimiter;
import com.nexorix.transaction.TransactionRepository;
import com.nexorix.transaction.TransactionService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    /** Resultado de recibir un aviso. */
    public enum Outcome { REGISTERED, DUPLICATE, IGNORED }

    private final NotificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final SecureRandom random = new SecureRandom();
    private final RateLimiter limiter = new RateLimiter(120, Duration.ofMinutes(1));

    public NotificationService(NotificationTokenRepository tokens, UserRepository userRepository,
                               AccountRepository accountRepository, AccountService accountService,
                               TransactionService transactionService,
                               TransactionRepository transactionRepository) {
        this.tokens = tokens;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
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

        LocalDateTime when = whenOf(postedAtMillis);
        String reference = "NTF-" + hash(user.getId() + "|" + cleanPackage + "|" + postedAtMillis + "|"
                + cleanTitle + "|" + cleanText).substring(0, 24).toUpperCase();
        if (transactionRepository.existsByAccountUserIdAndReference(user.getId(), reference)) {
            saved.markUsed(false);
            return Outcome.DUPLICATE;
        }

        Account account = accountFor(user, movement);
        transactionService.saveFromNotification(account, movement.amount(), movement.type(),
                movement.description(), when, reference, movement.balance());
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

    private static LocalDateTime whenOf(Long millis) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (millis == null || millis <= 0) {
            return now;
        }
        LocalDateTime posted = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
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
