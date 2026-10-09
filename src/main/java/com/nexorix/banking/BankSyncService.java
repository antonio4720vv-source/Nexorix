package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.fraud.AppNotification;
import com.nexorix.fraud.AppNotification.Severity;
import com.nexorix.fraud.AppNotificationRepository;
import com.nexorix.fraud.CriticalFraudAlertEvent;
import com.nexorix.fraud.FraudEngine;
import com.nexorix.fraud.FraudEngine.Level;
import com.nexorix.fraud.FraudEngine.Observation;
import com.nexorix.fraud.FraudEngine.Verdict;
import com.nexorix.fraud.Geo;
import com.nexorix.fraud.GeoLocator;
import com.nexorix.importer.TransactionClassifier;
import com.nexorix.split.SplitPaymentMatcher;
import com.nexorix.split.SplitShare;
import com.nexorix.split.SplitShareRepository;
import com.nexorix.transaction.PurchaseRegisteredEvent;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.whatsapp.PurchaseQuestionNotifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Corazon de la sincronizacion bancaria: recibe un movimiento normalizado y decide, en orden:
 *
 *   1. clasificar (gasto / transferencia interna / a tercero / ingreso)
 *   2. antifraude: imposibilidad fisica -> BLOQUEA y alerta por app + WhatsApp + SMS
 *   3. si pasa: guarda la transaccion, aprende el comportamiento y avisa si es una anomalia (solo app)
 *   4. si es gasto o transferencia a tercero y la persona activo el opt-in: pregunta por WhatsApp
 *
 * Todo en una sola transaccion; WhatsApp / SMS salen despues del commit y en otro hilo.
 */
@Service
public class BankSyncService {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");

    public record Result(String status, String kind, Long eventId, String risk, String message) {
    }

    private final BankLinkRepository links;
    private final BankEventRepository events;
    private final UserRepository users;
    private final TransactionService transactions;
    private final FraudEngine fraud;
    private final GeoLocator geoLocator;
    private final AppNotificationRepository notifications;
    private final ApplicationEventPublisher publisher;
    private final SplitShareRepository splitShares;

    public BankSyncService(BankLinkRepository links, BankEventRepository events, UserRepository users,
                           TransactionService transactions, FraudEngine fraud, GeoLocator geoLocator,
                           AppNotificationRepository notifications, ApplicationEventPublisher publisher,
                           SplitShareRepository splitShares) {
        this.links = links;
        this.events = events;
        this.users = users;
        this.transactions = transactions;
        this.fraud = fraud;
        this.geoLocator = geoLocator;
        this.notifications = notifications;
        this.publisher = publisher;
        this.splitShares = splitShares;
    }

    @Transactional
    public Result process(BankWebhookPayload p) {
        validate(p);
        if (events.existsByEventId(p.eventId())) {
            return new Result("DUPLICATE", null, null, "NONE", "Evento ya procesado.");
        }
        BankLink link = links.findByExternalRef(p.accountRef())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta bancaria no vinculada."));
        User user = link.getUser();
        Set<String> ownRefs = links.findByUserIdOrderByIdAsc(user.getId()).stream()
                .map(BankLink::getExternalRef).collect(Collectors.toSet());

        BankMovementKind kind = BankMovementClassifier.classify(p, ownRefs, user.getCedula());
        BankWebhookPayload.Counterparty other = p.counterparty();
        String label = kind == BankMovementKind.EXPENSE ? p.merchant() : other == null ? null : other.name();
        if (label == null || label.isBlank()) {
            label = p.merchant() != null && !p.merchant().isBlank() ? p.merchant() : "Movimiento bancario";
        }

        BankWebhookPayload.Location where = p.location();
        Geo geo = where == null ? null
                : geoLocator.resolve(where.city(), where.country(), where.latitude(), where.longitude());

        BankEvent event = new BankEvent(p.eventId(), user, link.getAccount(), link.getBank(), kind,
                p.direction().trim().toUpperCase(Locale.ROOT), p.amount(), label.trim(),
                p.occurredAt().atZoneSameInstant(ZONE).toLocalDateTime());
        if (where != null) {
            event.place(geo == null ? where.city() : geo.city(), geo == null ? where.country() : geo.country(),
                    geo == null ? null : geo.latitude(), geo == null ? null : geo.longitude(), where.ip());
        }
        if (other != null) {
            event.counterpartyRef(other.accountRef() != null ? other.accountRef() : other.document());
        }

        Verdict verdict = worst(fraud.evaluate(user, observation(event, geo)),
                cardCheck(link.getAccount(), p, event.getOccurredAt().toLocalDate()));
        event.risk(verdict.level().name(), verdict.reason());

        if (verdict.level() == Level.CRITICAL) {
            event.blocked();
            events.save(event);
            alertCritical(user, event, verdict);
            return new Result("BLOCKED", kind.name(), event.getId(), "CRITICAL", verdict.reason());
        }
        return apply(event, user, geo, verdict);
    }

    /** La persona confirma "fui yo": el movimiento bloqueado se aplica. */
    @Transactional
    public Result release(String username, Long eventId) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
        BankEvent event = events.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado."));
        if (event.getStatus() != BankEvent.Status.BLOCKED) {
            throw new IllegalArgumentException("Ese movimiento no está bloqueado.");
        }
        Geo geo = event.getLatitude() == null ? null
                : new Geo(event.getCity(), event.getCountry(), event.getLatitude(), event.getLongitude());
        return apply(event, user, geo, new Verdict(Level.NONE, null, null, 0, 0), BankEvent.Status.RELEASED);
    }

    private Result apply(BankEvent event, User user, Geo geo, Verdict verdict) {
        return apply(event, user, geo, verdict, BankEvent.Status.APPLIED);
    }

    private Result apply(BankEvent event, User user, Geo geo, Verdict verdict, BankEvent.Status finalStatus) {
        BankMovementKind kind = event.getKind();
        boolean debit = event.getDirection().equals("DEBIT");
        String description = switch (kind) {
            case EXPENSE -> event.getLabel();
            case THIRD_PARTY_TRANSFER -> "Transferencia a " + event.getLabel();
            case INTERNAL_TRANSFER -> (debit ? "Envío a " : "Recibido de ") + event.getLabel();
            case INCOME -> "Recibido de " + event.getLabel();
        };
        String reference = ("BANK-" + event.getEventId());

        Transaction saved = transactions.saveFromBank(event.getAccount(), event.getAmount(),
                debit ? "EGRESO" : "INGRESO", description, event.getOccurredAt(),
                reference.length() > 150 ? reference.substring(0, 150) : reference);
        event.applied(finalStatus, saved.getId());
        events.save(event);

        fraud.learn(user, observation(event, geo));

        // Dinero que llega de una persona: si cuadra con lo que te debe por Dividir gastos, queda saldado solo.
        if (!debit) {
            settleSplitPayment(event, user);
        }

        // Anomalia leve: solo aparece dentro de la app, sin alarmar por canales externos.
        if (verdict.level() == Level.ANOMALY) {
            notifications.save(new AppNotification(user, Severity.WARNING, "Movimiento inusual",
                    verdict.reason() + " (" + PurchaseQuestionNotifier.money(event.getAmount()) + ").", event.getId()));
        }

        // Opt-in: sin el flag, los gastos comunes no generan ningun WhatsApp.
        boolean asksContext = kind == BankMovementKind.EXPENSE || kind == BankMovementKind.THIRD_PARTY_TRANSFER;
        if (asksContext && user.isWhatsappNotificationsEnabled()) {
            publisher.publishEvent(new PurchaseRegisteredEvent(saved.getId(), user.getId(), event.getAmount(),
                    kind == BankMovementKind.EXPENSE ? event.getLabel() : "una transferencia a " + event.getLabel(),
                    event.getOccurredAt()));
        }
        return new Result(finalStatus.name(), kind.name(), event.getId(), verdict.level().name(), verdict.reason());
    }

    /**
     * Antirrobo por tarjeta: si el banco informa con que tarjeta se hizo la compra y la cuenta tiene sus datos,
     * una tarjeta vencida o distinta de la registrada es sospechosa. (La vencida bloquea; la distinta solo alerta.)
     */
    static Verdict cardCheck(com.nexorix.account.Account account, BankWebhookPayload p, java.time.LocalDate day) {
        if (p.cardLast4() == null || p.cardLast4().isBlank() || !"DEBIT".equalsIgnoreCase(p.direction())) {
            return new Verdict(Level.NONE, null, null, 0, 0);
        }
        if (account.isCardExpiredOn(day)) {
            return new Verdict(Level.CRITICAL, "Se usó una tarjeta vencida (••" + p.cardLast4() + ").", null, 0, 0);
        }
        if (account.getCardLast4() != null && !account.getCardLast4().equals(p.cardLast4().trim())) {
            return new Verdict(Level.ANOMALY, "Compra con una tarjeta (••" + p.cardLast4().trim()
                    + ") que no es la registrada en esta cuenta (••" + account.getCardLast4() + ").", null, 0, 0);
        }
        return new Verdict(Level.NONE, null, null, 0, 0);
    }

    static Verdict worst(Verdict a, Verdict b) {
        return b.level().ordinal() >= a.level().ordinal() && b.level() != Level.NONE ? b : a;
    }

    private void settleSplitPayment(BankEvent event, User user) {
        String name = event.getKind() == BankMovementKind.EXPENSE ? null : event.getLabel();
        String label = name == null || name.equals("Movimiento bancario") ? null : name;
        String ref = event.getCounterpartyRef();
        SplitPaymentMatcher.pick(splitShares.findOpenForPayerByAmount(user.getId(), event.getAmount()), label, ref)
                .ifPresent(share -> {
                    share.settle();
                    splitShares.save(share);
                    String title = share.getExpense().getTitle() == null || share.getExpense().getTitle().isBlank()
                            ? "una cuenta dividida" : "«" + share.getExpense().getTitle() + "»";
                    notifications.save(new AppNotification(user, Severity.INFO, "Te pagaron una división",
                            share.getParticipant().getName() + " te pagó " + PurchaseQuestionNotifier.money(event.getAmount())
                                    + " de " + title + ". Lo marcamos como saldado.", event.getId()));
                });
    }

    private void alertCritical(User user, BankEvent event, Verdict verdict) {
        String amount = PurchaseQuestionNotifier.money(event.getAmount());
        String where = new Geo(event.getCity(), event.getCountry(), 0, 0).label();
        String detail = "%s en %s (%s) quedó bloqueado. %s".formatted(amount, event.getLabel(), where, verdict.reason());
        AppNotification notification = notifications.save(new AppNotification(user, Severity.CRITICAL,
                "🚨 Movimiento bloqueado por seguridad", detail, event.getId()));
        publisher.publishEvent(new CriticalFraudAlertEvent(user.getId(), notification.getId(),
                "Nexorix ALERTA: bloqueamos " + amount + " en " + event.getLabel() + " (" + where
                        + "), imposible fisicamente respecto a tu ultima compra. Si no fuiste tu, llama a tu banco ya.",
                "🚨 *Nexorix – Alerta de seguridad*\nBloqueamos " + amount + " en " + event.getLabel() + " (" + where
                        + ").\n" + verdict.reason() + "\nSi fuiste tú, confírmalo en la app. Si no, llama a tu banco ya."));
    }

    private Observation observation(BankEvent event, Geo geo) {
        return new Observation(event.getKind(), event.getLabel(),
                TransactionClassifier.classify(event.getLabel(), "EGRESO"), event.getLabel(),
                event.getCounterpartyRef(), event.getAmount(), geo, event.getIp(), event.getOccurredAt());
    }

    private static void validate(BankWebhookPayload p) {
        if (p == null || blank(p.eventId()) || blank(p.accountRef()) || blank(p.direction()) || p.occurredAt() == null) {
            throw new IllegalArgumentException("Faltan campos: eventId, accountRef, direction y occurredAt.");
        }
        if (p.eventId().length() > 100) {
            throw new IllegalArgumentException("eventId demasiado largo.");
        }
        if (!p.direction().trim().equalsIgnoreCase("DEBIT") && !p.direction().trim().equalsIgnoreCase("CREDIT")) {
            throw new IllegalArgumentException("direction debe ser DEBIT o CREDIT.");
        }
        if (p.amount() == null || p.amount().compareTo(BigDecimal.ZERO) <= 0
                || p.amount().compareTo(TransactionService.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        }
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
