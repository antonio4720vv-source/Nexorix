package com.nexorix.trace;

import com.nexorix.account.Account;
import com.nexorix.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.nexorix.trace.TraceTestData.account;
import static com.nexorix.trace.TraceTestData.in;
import static com.nexorix.trace.TraceTestData.out;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceMatcherTest {

    private final Account nequi = account(1, "Nequi");
    private final Account davivienda = account(2, "Davivienda");
    private final Account nu = account(3, "Nu");

    // ------------------------------------------------------------
    // 1 -> 1
    // ------------------------------------------------------------

    @Test
    void unaTransferenciaExactaYRapidaEsMuyProbable() {
        List<TraceSuggestion> result = TraceMatcher.suggest(List.of(
                out(1, nequi, 1_000_000, 0), in(2, davivienda, 1_000_000, 5)), Set.of());

        assertThat(result).hasSize(1);
        TraceSuggestion s = result.get(0);
        assertThat(s.kind()).isEqualTo(TraceKind.ONE_TO_ONE);
        assertThat(s.score()).isEqualTo(90);
        assertThat(s.classification()).isEqualTo("TRANSFERENCIA PROPIA MUY PROBABLE");
        assertThat(s.fee()).isEqualByComparingTo("0");
        assertThat(s.key()).isEqualTo("o:1|d:2");
    }

    @Test
    void conComisionInterbancariaIgualSeDetectaYSeRegistraLaComision() {
        List<TraceSuggestion> result = TraceMatcher.suggest(List.of(
                out(1, nequi, 1_000_000, 0), in(2, davivienda, 995_000, 30)), Set.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fee()).isEqualByComparingTo("5000");
        assertThat(result.get(0).amount()).isEqualByComparingTo("995000");
        assertThat(result.get(0).score()).isEqualTo(75); // 40 + 15 + 20
    }

    @Test
    void unaDiferenciaMuyGrandeNoEsComision() {
        assertThat(TraceMatcher.suggest(List.of(
                out(1, nequi, 1_000_000, 0), in(2, davivienda, 900_000, 5)), Set.of())).isEmpty();
    }

    @Test
    void siLlegaMasDeLoQueSalioNoEsTransferencia() {
        assertThat(TraceMatcher.suggest(List.of(
                out(1, nequi, 100_000, 0), in(2, davivienda, 101_000, 5)), Set.of())).isEmpty();
    }

    @Test
    void laMismaCuentaOMasDe48HorasNoCuentan() {
        assertThat(TraceMatcher.suggest(List.of(
                out(1, nequi, 50_000, 0), in(2, nequi, 50_000, 5)), Set.of())).isEmpty();
        assertThat(TraceMatcher.suggest(List.of(
                out(1, nequi, 50_000, 0), in(2, nu, 50_000, 49 * 60)), Set.of())).isEmpty();
    }

    @Test
    void losMovimientosYaConciliadosNoSeSugierenOtraVez() {
        assertThat(TraceMatcher.suggest(List.of(
                out(1, nequi, 50_000, 0), in(2, nu, 50_000, 5)), Set.of(1L))).isEmpty();
    }

    // ------------------------------------------------------------
    // 1 -> N y N -> 1
    // ------------------------------------------------------------

    @Test
    void unaSalidaRepartidaEnDosCuentasEsUnoAVarios() {
        List<TraceSuggestion> result = TraceMatcher.suggest(List.of(
                out(1, nequi, 1_000_000, 0),
                in(2, davivienda, 600_000, 10),
                in(3, nu, 400_000, 12),
                in(4, nu, 77_000, 15)), Set.of());

        TraceSuggestion group = result.stream().filter(s -> s.kind() == TraceKind.ONE_TO_MANY).findFirst().orElseThrow();
        assertThat(group.destinationIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(group.amount()).isEqualByComparingTo("1000000");
        assertThat(group.score()).isEqualTo(85); // 40 + 30 + 20 - 5
    }

    @Test
    void variasSalidasQueLleganJuntasSonVariosAUno() {
        List<TraceSuggestion> result = TraceMatcher.suggest(List.of(
                out(1, nequi, 300_000, 0),
                out(2, davivienda, 200_000, 20),
                in(3, nu, 500_000, 40)), Set.of());

        TraceSuggestion group = result.stream().filter(s -> s.kind() == TraceKind.MANY_TO_ONE).findFirst().orElseThrow();
        assertThat(group.originIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(group.fee()).isEqualByComparingTo("0");
    }

    @Test
    void siHayCoincidenciaExactaUnoAUnoNoSeArmanGrupos() {
        List<TraceSuggestion> result = TraceMatcher.suggest(List.of(
                out(1, nequi, 1_000_000, 0),
                in(2, davivienda, 1_000_000, 5),
                in(3, nu, 600_000, 6),
                in(4, nu, 400_000, 7)), Set.of());

        assertThat(result).allMatch(s -> s.kind() == TraceKind.ONE_TO_ONE);
    }

    // ------------------------------------------------------------
    // Validar lo que confirma la persona
    // ------------------------------------------------------------

    @Test
    void validarRechazaGruposInvalidos() {
        Transaction o1 = out(1, nequi, 100_000, 0);
        Transaction o2 = out(2, nequi, 100_000, 0);
        Transaction d1 = in(3, nu, 100_000, 5);
        Transaction d2 = in(4, davivienda, 100_000, 5);

        assertThatThrownBy(() -> TraceMatcher.validate(List.of(o1, o2), List.of(d1, d2)))
                .hasMessageContaining("N → N");
        assertThatThrownBy(() -> TraceMatcher.validate(List.of(d1), List.of(o1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TraceMatcher.validate(List.of(o1), List.of(in(5, nequi, 100_000, 5))))
                .hasMessageContaining("cuentas diferentes");
        assertThatThrownBy(() -> TraceMatcher.validate(List.of(o1), List.of(in(6, nu, 10_000, 5))))
                .hasMessageContaining("no cuadran");
    }

    @Test
    void laToleranciaEsRazonable() {
        assertThat(TraceMatcher.tolerance(new BigDecimal("1000000"))).isEqualByComparingTo("10000");
        assertThat(TraceMatcher.tolerance(new BigDecimal("50000"))).isEqualByComparingTo("5000");
        assertThat(TraceMatcher.tolerance(new BigDecimal("10000"))).isEqualByComparingTo("1000");
        assertThat(TraceMatcher.tolerance(new BigDecimal("5000000"))).isEqualByComparingTo("20000");
    }
}
