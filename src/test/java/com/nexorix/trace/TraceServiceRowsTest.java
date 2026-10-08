package com.nexorix.trace;

import com.nexorix.account.Account;
import com.nexorix.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.nexorix.trace.TraceTestData.account;
import static com.nexorix.trace.TraceTestData.in;
import static com.nexorix.trace.TraceTestData.out;
import static org.assertj.core.api.Assertions.assertThat;

/** Como se guardan los grupos y como afectan el dinero real. */
class TraceServiceRowsTest {

    private final Account nequi = account(1, "Nequi");
    private final Account davivienda = account(2, "Davivienda");
    private final Account nu = account(3, "Nu");

    @Test
    void unoAVariosGuardaUnaFilaPorDestinoYLaComisionUnaSolaVez() {
        Transaction o = out(1, nequi, 1_005_000, 0);
        TraceSuggestion group = TraceMatcher.validate(List.of(o),
                List.of(in(2, davivienda, 600_000, 5), in(3, nu, 400_000, 5)));

        List<ReconciliationMatch> rows = TraceService.buildRows(group, "g1");

        assertThat(rows).hasSize(2).allMatch(r -> "g1".equals(r.getGroupId()));
        assertThat(rows.stream().map(ReconciliationMatch::effectiveFee).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("5000");

        Map<Long, BigDecimal> internal = MoneyFlowService.internalPortions(rows);
        assertThat(internal.get(1L)).isEqualByComparingTo("1000000"); // los $5.000 restantes son gasto real
        assertThat(internal.get(2L)).isEqualByComparingTo("600000");
    }

    @Test
    void variosAUnoDescuentaLaComisionDelOrigenMasGrande() {
        Transaction big = out(1, nequi, 300_000, 0);
        Transaction small = out(2, davivienda, 205_000, 0);
        TraceSuggestion group = TraceMatcher.validate(List.of(big, small), List.of(in(3, nu, 500_000, 10)));

        List<ReconciliationMatch> rows = TraceService.buildRows(group, "g2");
        Map<Long, BigDecimal> internal = MoneyFlowService.internalPortions(rows);

        assertThat(group.fee()).isEqualByComparingTo("5000");
        assertThat(internal.get(1L)).isEqualByComparingTo("295000");
        assertThat(internal.get(2L)).isEqualByComparingTo("205000");
        assertThat(internal.get(3L)).isEqualByComparingTo("500000");
    }

    @Test
    void deshacerDejaLaFilaEnElHistorialPeroInactiva() {
        TraceSuggestion group = TraceMatcher.validate(List.of(out(1, nequi, 100_000, 0)),
                List.of(in(2, nu, 100_000, 1)));
        ReconciliationMatch row = TraceService.buildRows(group, "g3").get(0);

        row.undo();

        assertThat(row.isActive()).isFalse();
        assertThat(row.getUndoneAt()).isNotNull();
        assertThat(row.getStatus()).isEqualTo(ReconciliationStatus.UNMATCHED);
    }

    @Test
    void elHistorialAgrupaLasFilas() {
        TraceSuggestion group = TraceMatcher.validate(List.of(out(1, nequi, 1_000_000, 0)),
                List.of(in(2, davivienda, 600_000, 5), in(3, nu, 400_000, 5)));

        TraceGroupView view = TraceService.toView("g4", TraceService.buildRows(group, "g4"));

        assertThat(view.kindLabel()).isEqualTo("1 → N");
        assertThat(view.origins()).hasSize(1);
        assertThat(view.destinations()).hasSize(2);
        assertThat(view.amount()).isEqualByComparingTo("1000000");
        assertThat(view.active()).isTrue();
    }
}
