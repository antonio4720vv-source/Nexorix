package com.nexorix.banking;

import com.nexorix.banking.BankWebhookPayload.Counterparty;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankMovementClassifierTest {

    private static final Set<String> OWN = Set.of("nequi-1", "bancolombia-1");

    private static BankWebhookPayload payload(String direction, String channel, Counterparty other) {
        return new BankWebhookPayload("e1", "NEQUI", "nequi-1", direction, new BigDecimal("1000"), "COP",
                channel, "Éxito", other, null, OffsetDateTime.now());
    }

    @Test
    void compraConTarjetaOEnDatafonoEsGasto() {
        assertThat(BankMovementClassifier.classify(payload("DEBIT", "CARD_PURCHASE", null), OWN, "99"))
                .isEqualTo(BankMovementKind.EXPENSE);
        assertThat(BankMovementClassifier.classify(payload("debit", "pos", null), OWN, "99"))
                .isEqualTo(BankMovementKind.EXPENSE);
    }

    @Test
    void transferirAOtraCuentaPropiaEsInternaAunqueSeaOtroBanco() {
        assertThat(BankMovementClassifier.classify(
                payload("DEBIT", "TRANSFER", new Counterparty("Yo", "bancolombia-1", null)), OWN, "99"))
                .isEqualTo(BankMovementKind.INTERNAL_TRANSFER);
    }

    @Test
    void transferirAMiMismoDocumentoEsInterna() {
        assertThat(BankMovementClassifier.classify(
                payload("DEBIT", "TRANSFER", new Counterparty("Yo", "otra", "99.000")), OWN, "99000"))
                .isEqualTo(BankMovementKind.INTERNAL_TRANSFER);
    }

    @Test
    void transferirAOtraPersonaEsATercero() {
        assertThat(BankMovementClassifier.classify(
                payload("DEBIT", "TRANSFER", new Counterparty("Carlos", "nequi-9", "123")), OWN, "99"))
                .isEqualTo(BankMovementKind.THIRD_PARTY_TRANSFER);
    }

    @Test
    void laMismaCuentaNoCuentaComoPropia() {
        assertThat(BankMovementClassifier.classify(
                payload("DEBIT", "TRANSFER", new Counterparty("Yo", "nequi-1", null)), OWN, "99"))
                .isEqualTo(BankMovementKind.THIRD_PARTY_TRANSFER);
    }

    @Test
    void dineroQueEntraDeUnTerceroEsIngreso() {
        assertThat(BankMovementClassifier.classify(
                payload("CREDIT", "TRANSFER", new Counterparty("Carlos", "nequi-9", "123")), OWN, "99"))
                .isEqualTo(BankMovementKind.INCOME);
    }

    @Test
    void canalDesconocidoSeRechaza() {
        assertThatThrownBy(() -> BankMovementClassifier.classify(payload("DEBIT", "CHEQUE", null), OWN, "99"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
