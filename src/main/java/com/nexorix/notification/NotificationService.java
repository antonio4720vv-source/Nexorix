package com.nexorix.notification;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.ai.RateLimiter;
import com.nexorix.fraud.Geo;
import com.nexorix.fraud.GeoLocator;
import com.nexorix.transaction.TransactionService;
import com.nexorix.whatsapp.PurchaseQuestionNotifier;
import org.springframework.data.domain.PageRequest;
import com.nexorix.banking.BankEventRepository;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
    public enum Outcome { REGISTERED, DUPLICATE, IGNORED, UNRECOGNIZED }

    private final NotificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final BankSyncService sync;
    private final BankLinkService linkService;
    private final NotificationLogRepository logs;
    private final BankEventRepository events;
    private final GeoLocator geoLocator;
    private final SecureRandom random = new SecureRandom();
    private final RateLimiter limiter = new RateLimiter(120, Duration.ofMinutes(1));

    /** Un aviso identico (banco, sentido, monto y comercio) en menos de este tiempo es el mismo. */
    static final Duration REPEAT_WINDOW = Duration.ofSeconds(90);

    public NotificationService(NotificationTokenRepository tokens, UserRepository userRepository,
                               AccountRepository accountRepository, AccountService accountService,
                               BankSyncService sync, BankLinkService linkService,
                               NotificationLogRepository logs, BankEventRepository events,
                               GeoLocator geoLocator) {
        this.tokens = tokens;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.sync = sync;
        this.linkService = linkService;
        this.logs = logs;
        this.events = events;
        this.geoLocator = geoLocator;
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
    public Outcome ingest(String token, String packageName, String appName, String title, String text,
                          Long postedAtMillis) {
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
        String cleanApp = cut(appName).trim();

        NotificationParser.Analysis analysis =
                NotificationParser.analyze(cleanPackage, cleanApp, cleanTitle, cleanText);
        String appLabel = cleanApp.isEmpty() ? cleanPackage : cleanApp;
        String bankName = analysis.bank() == null ? null : analysis.bank().name();
        purgeOldLogsSometimes();

        switch (analysis.kind()) {
            case NOT_BANK -> {
                // No es de un banco: ni se guarda ni se cuenta.
                saved.markUsed(false);
                return Outcome.IGNORED;
            }
            case SENSITIVE -> {
                record(user, appLabel, bankName, NotificationLog.SENSITIVE,
                        "Aviso con una clave o código: no se guarda su texto.", null);
                saved.markUsed(false);
                return Outcome.IGNORED;
            }
            case INFO -> {
                record(user, appLabel, bankName, NotificationLog.INFO,
                        "Aviso del banco sin un movimiento de dinero.", null);
                saved.markUsed(false);
                return Outcome.IGNORED;
            }
            case UNRECOGNIZED -> {
                record(user, appLabel, bankName, NotificationLog.UNRECOGNIZED,
                        "Parece un movimiento, pero no se entendió. Revisa el texto.",
                        (cleanTitle + " " + cleanText).trim());
                saved.markUsed(false);
                return Outcome.UNRECOGNIZED;
            }
            default -> { }
        }

        ParsedNotification movement = analysis.movement();
        boolean debit = movement.type().equals("EGRESO");
        String label = movement.label() == null || movement.label().isBlank() ? "Movimiento bancario" : movement.label();
        String what = (movement.purchase() ? "Compra" : debit ? "Salida" : "Ingreso") + " de "
                + PurchaseQuestionNotifier.money(movement.amount()) + " · " + label;

        // Mismo movimiento avisado dos veces seguidas (el banco repite el aviso o Android lo vuelve a publicar).
        boolean repeated = events.existsByUserIdAndBankAndDirectionAndAmountAndLabelIgnoreCaseAndCreatedAtAfter(
                user.getId(), movement.bank().name().toUpperCase(Locale.ROOT), debit ? "DEBIT" : "CREDIT",
                movement.amount().setScale(2, java.math.RoundingMode.HALF_UP), label,
                LocalDateTime.now().minus(REPEAT_WINDOW));
        if (repeated) {
            record(user, appLabel, bankName, NotificationLog.DUPLICATE, "Repetido, no se registró otra vez: " + what, null);
            saved.markUsed(false);
            return Outcome.DUPLICATE;
        }

        OffsetDateTime when = whenOf(postedAtMillis);
        // Mismo aviso (mismo texto y hora) = mismo evento: tampoco se registra dos veces.
        String eventId = "ntf-" + hash(user.getId() + "|" + cleanPackage + "|" + postedAtMillis + "|"
                + cleanTitle + "|" + cleanText).substring(0, 32);

        Account account = accountFor(user, movement);
        BankLink link = linkService.linkFor(user, account);

        String channel = movement.purchase() ? "POS" : "TRANSFER";
        BankWebhookPayload.Counterparty other = !movement.purchase() && movement.label() != null
                ? new BankWebhookPayload.Counterparty(movement.label(), null, null) : null;
        // Si el aviso nombra una ciudad (el comercio), el antifraude puede comparar distancias entre compras.
        Geo place = geoLocator.findInText(cleanTitle + " " + cleanText).orElse(null);
        BankWebhookPayload.Location where = place == null ? null
                : new BankWebhookPayload.Location(place.city(), place.country(), place.latitude(), place.longitude(), null);
        BankSyncService.Result result = sync.process(new BankWebhookPayload(eventId, movement.bank().name(),
                link.getExternalRef(), debit ? "DEBIT" : "CREDIT", movement.amount(), "COP", channel,
                movement.purchase() ? movement.label() : null, other, where, when));

        if (result.status().equals("DUPLICATE")) {
            record(user, appLabel, bankName, NotificationLog.DUPLICATE, "Repetido, no se registró otra vez: " + what, null);
            saved.markUsed(false);
            return Outcome.DUPLICATE;
        }
        if (result.status().equals("BLOCKED")) {
            record(user, appLabel, bankName, NotificationLog.BLOCKED,
                    "Bloqueado por seguridad: " + what + ". " + (result.message() == null ? "" : result.message()), null);
            saved.markUsed(true);
            return Outcome.REGISTERED;
        }
        // Si el aviso dice el saldo, ese es el saldo real de la cuenta.
        if (movement.balance() != null && movement.balance().compareTo(TransactionService.MAX_AMOUNT) <= 0) {
            account.setBalance(movement.balance().setScale(2, java.math.RoundingMode.HALF_UP));
            accountRepository.save(account);
        }
        record(user, appLabel, bankName, NotificationLog.REGISTERED,
                what + (place == null ? "" : " (" + place.label() + ")"), null);
        saved.markUsed(true);
        return Outcome.REGISTERED;
    }

    private void record(User user, String appName, String bank, String result, String summary, String snippet) {
        logs.save(new NotificationLog(user, appName, bank, result, summary, snippet));
    }

    /** De vez en cuando se borran los registros de mas de 90 dias. */
    private void purgeOldLogsSometimes() {
        if (random.nextInt(100) == 0) {
            logs.deleteOlderThan(LocalDateTime.now().minusDays(90));
        }
    }

    /** Los ultimos avisos de la persona, del mas nuevo al mas viejo. */
    @Transactional(readOnly = true)
    public List<NotificationLog> recent(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return logs.findByUserIdOrderByReceivedAtDescIdDesc(user.getId(), PageRequest.of(0, 50));
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
