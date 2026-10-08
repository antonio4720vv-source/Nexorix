package com.nexorix.banking;

import com.nexorix.banking.BankWebhookPayload.Counterparty;
import com.nexorix.banking.BankWebhookPayload.Location;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Simulador del agregador bancario para la demo: arma webhooks como los de Plaid / Prometeo
 * y los pasa por el mismo flujo que el webhook real (sin firma, porque ya hay sesion iniciada).
 * Se apaga con NEXORIX_BANK_DEMO=false.
 */
@Service
public class BankDemoService {

    private static final Location BOGOTA = new Location("Bogotá", "CO", null, null, "190.24.10.5");
    private static final Location MIAMI = new Location("Miami", "US", null, null, "66.249.64.1");

    private final BankSyncService sync;
    private final BankLinkRepository links;
    private final UserRepository users;
    private final boolean enabled;

    public BankDemoService(BankSyncService sync, BankLinkRepository links, UserRepository users,
                           @Value("${nexorix.bank.demo-enabled:true}") boolean enabled) {
        this.sync = sync;
        this.links = links;
        this.users = users;
        this.enabled = enabled;
    }

    /**
     * Escenarios: SEMILLA (6 compras habituales), COMPRA, COMPRA_INUSUAL, TRANSFER_PROPIA,
     * TRANSFER_TERCERO, VIAJE_IMPOSIBLE (compra en Bogota hace 1 h y otra en Miami ahora).
     */
    public List<BankSyncService.Result> simulate(String username, String scenario) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La demo bancaria está apagada.");
        }
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
        List<BankLink> mine = links.findByUserIdOrderByIdAsc(user.getId());
        if (mine.isEmpty()) {
            throw new IllegalArgumentException("Primero vincula una cuenta a un banco.");
        }
        BankLink from = mine.get(0);
        String ref = from.getExternalRef();
        OffsetDateTime now = OffsetDateTime.now();

        return switch (scenario.toUpperCase(Locale.ROOT)) {
            case "SEMILLA" -> {
                String[] shops = {"Éxito", "Rappi", "Starbucks", "Éxito", "Rappi", "Éxito"};
                List<BankSyncService.Result> results = new java.util.ArrayList<>();
                for (int i = 0; i < shops.length; i++) {
                    results.add(sync.process(card(ref, shops[i], 25_000 + i * 3_000, BOGOTA, now.minusDays(shops.length - i))));
                }
                yield results;
            }
            case "COMPRA" -> List.of(sync.process(card(ref, "Éxito", 48_900, BOGOTA, now)));
            case "COMPRA_INUSUAL" -> List.of(sync.process(card(ref, "Joyería Diamante Real", 1_250_000, BOGOTA, now)));
            case "TRANSFER_PROPIA" -> List.of(sync.process(transfer(ref, "Mis ahorros",
                    new Counterparty(user.getName(), null, user.getCedula()), 200_000, now)));
            case "TRANSFER_TERCERO" -> List.of(sync.process(transfer(ref, "Carlos Pérez",
                    new Counterparty("Carlos Pérez", "nequi-3001112233", "1020304050"), 80_000, now)));
            case "VIAJE_IMPOSIBLE" -> List.of(
                    sync.process(card(ref, "Éxito", 52_000, BOGOTA, now.minusHours(1))),
                    sync.process(card(ref, "Best Buy Miami", 3_400_000, MIAMI, now)));
            default -> throw new IllegalArgumentException("Escenario desconocido: " + scenario);
        };
    }

    private static BankWebhookPayload card(String ref, String merchant, long amount, Location where, OffsetDateTime at) {
        return new BankWebhookPayload("demo-" + UUID.randomUUID(), "DEMO", ref, "DEBIT", BigDecimal.valueOf(amount),
                "COP", "CARD_PURCHASE", merchant, null, where, at);
    }

    private static BankWebhookPayload transfer(String ref, String label, Counterparty to, long amount, OffsetDateTime at) {
        return new BankWebhookPayload("demo-" + UUID.randomUUID(), "DEMO", ref, "DEBIT", BigDecimal.valueOf(amount),
                "COP", "TRANSFER", label, to, null, at);
    }
}
