package com.nexorix.fraud;

import com.nexorix.banking.BankMovementKind;
import com.nexorix.fraud.FraudEngine.Level;
import com.nexorix.fraud.FraudEngine.Observation;
import com.nexorix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudEngineTest {

    private final GeoLocator locator = new GeoLocator();
    private final Geo bogota = locator.resolve("Bogotá", "CO", null, null);
    private final Geo miami = locator.resolve("Miami", "US", null, null);
    private final Geo medellin = locator.resolve("Medellín", "CO", null, null);
    private final LocalDateTime tenAm = LocalDateTime.of(2026, 5, 4, 10, 0);

    private UserBehaviorLogRepository logs;
    private FraudEngine engine;
    private User ana;

    @BeforeEach
    void setUp() {
        logs = mock(UserBehaviorLogRepository.class);
        engine = new FraudEngine(logs, 900, 150, 5);
        ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
    }

    private void lastPurchase(Geo geo, LocalDateTime at) {
        UserBehaviorLog log = new UserBehaviorLog(ana, UserBehaviorLog.EventType.PURCHASE, at)
                .merchant("exito", "Éxito", "MERCADO").place(geo, null);
        when(logs.findFirstByUserIdAndLatitudeNotNullAndOccurredAtBeforeOrderByOccurredAtDesc(anyLong(), any()))
                .thenReturn(Optional.of(log));
    }

    private Observation purchase(String merchant, Geo geo, LocalDateTime at) {
        return new Observation(BankMovementKind.EXPENSE, merchant, "OTROS", merchant, null,
                new BigDecimal("50000"), geo, null, at);
    }

    @Test
    void bogotaALas10yMiamiALas11EsImposibleFisicamente() {
        lastPurchase(bogota, tenAm);

        FraudEngine.Verdict verdict = engine.evaluate(ana, purchase("Best Buy", miami, tenAm.plusHours(1)));

        assertThat(verdict.level()).isEqualTo(Level.CRITICAL);
        assertThat(verdict.distanceKm()).isBetween(2_300.0, 2_700.0);
        assertThat(verdict.reason()).contains("Bogotá").contains("Miami");
    }

    @Test
    void bogotaAMedellinEnTresHorasEsPosible() {
        lastPurchase(bogota, tenAm);
        when(logs.existsByUserIdAndMerchantKey(anyLong(), anyString())).thenReturn(true);

        assertThat(engine.evaluate(ana, purchase("Éxito", medellin, tenAm.plusHours(3))).level()).isEqualTo(Level.NONE);
    }

    @Test
    void bogotaAMiamiEnUnVueloDeVerdadEsPosible() {
        lastPurchase(bogota, tenAm);
        when(logs.existsByUserIdAndMerchantKey(anyLong(), anyString())).thenReturn(true);

        assertThat(engine.evaluate(ana, purchase("Éxito", miami, tenAm.plusHours(8))).level()).isEqualTo(Level.NONE);
    }

    @Test
    void doblePuntoLejanoAlMismoInstanteEsImposible() {
        lastPurchase(bogota, tenAm);

        assertThat(engine.evaluate(ana, purchase("Best Buy", miami, tenAm)).level()).isEqualTo(Level.CRITICAL);
    }

    @Test
    void sinUbicacionNoHayReglaDeViaje() {
        lastPurchase(bogota, tenAm);
        when(logs.existsByUserIdAndMerchantKey(anyLong(), anyString())).thenReturn(true);

        assertThat(engine.evaluate(ana, purchase("Éxito", null, tenAm.plusMinutes(5))).level()).isEqualTo(Level.NONE);
    }

    @Test
    void comercioNuevoConHistorialEsAnomaliaLeve() {
        when(logs.countByUserId(1L)).thenReturn(8L);
        when(logs.existsByUserIdAndMerchantKey(1L, "joyeria diamante")).thenReturn(false);

        FraudEngine.Verdict verdict = engine.evaluate(ana, purchase("Joyería Diamante", bogota, tenAm));

        assertThat(verdict.level()).isEqualTo(Level.ANOMALY);
    }

    @Test
    void conPocoHistorialUnComercioNuevoNoAlarma() {
        when(logs.countByUserId(1L)).thenReturn(2L);

        assertThat(engine.evaluate(ana, purchase("Joyería Diamante", bogota, tenAm)).level()).isEqualTo(Level.NONE);
    }

    @Test
    void primeraTransferenciaAUnDesconocidoEsAnomalia() {
        Observation transfer = new Observation(BankMovementKind.THIRD_PARTY_TRANSFER, "Carlos", "OTROS", "Carlos",
                "nequi-9", new BigDecimal("80000"), null, null, tenAm);
        when(logs.existsByUserIdAndRecipientKey(1L, "nequi 9")).thenReturn(false);
        assertThat(engine.evaluate(ana, transfer).level()).isEqualTo(Level.ANOMALY);

        when(logs.existsByUserIdAndRecipientKey(1L, "nequi 9")).thenReturn(true);
        assertThat(engine.evaluate(ana, transfer).level()).isEqualTo(Level.NONE);
    }

    @Test
    void distanciaHaversineBogotaMiami() {
        assertThat(bogota.distanceKm(miami)).isBetween(2_300.0, 2_700.0);
    }
}
