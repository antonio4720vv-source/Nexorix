package com.nexorix.banking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Notificacion unificada de un movimiento, con la forma que daria un agregador tipo Plaid / Prometeo
 * (cada banco llega ya normalizado). Ejemplo en el README.
 *
 * direction: DEBIT (sale plata) o CREDIT (entra).  channel: CARD_PURCHASE, POS, ONLINE o TRANSFER.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BankWebhookPayload(
        String eventId,
        String bank,
        String accountRef,
        String direction,
        BigDecimal amount,
        String currency,
        String channel,
        String merchant,
        Counterparty counterparty,
        Location location,
        OffsetDateTime occurredAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Counterparty(String name, String accountRef, String document) {
    }

    /** Ciudad / pais del comercio o de la IP, o coordenadas exactas si el agregador las da. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String city, String country, Double latitude, Double longitude, String ip) {
    }
}
