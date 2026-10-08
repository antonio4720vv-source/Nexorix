package com.nexorix.trace;

import com.nexorix.account.Account;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.nexorix.trace.TraceTestData.account;
import static com.nexorix.trace.TraceTestData.in;
import static com.nexorix.trace.TraceTestData.out;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoneyFlowServiceTest {

    private static final String USER = "ana";

    private final Account nequi = account(1, "Nequi");
    private final Account davivienda = account(2, "Davivienda");
    private final Account nu = account(3, "Nu");

    private TransactionRepository transactionRepository;
    private ReconciliationMatchRepository matchRepository;
    private MoneyFlowService service;
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<ReconciliationMatch> matches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        matchRepository = mock(ReconciliationMatchRepository.class);
        TraceService traceService = new TraceService(transactionRepository, matchRepository);
        service = new MoneyFlowService(transactionRepository, matchRepository, traceService);

        when(transactionRepository.findByAccountUserUsernameOrderByTransactionDateDesc(USER)).thenReturn(transactions);
        when(matchRepository.findByOriginTransactionAccountUserUsernameOrderByCreatedAtDesc(USER)).thenReturn(matches);
    }

    private void confirmar(List<Transaction> origins, List<Transaction> destinations) {
        matches.addAll(TraceService.buildRows(TraceMatcher.validate(origins, destinations), "g" + matches.size()));
    }

    @Test
    void separaLasTransferenciasInternasDelDineroReal() {
        Transaction salida1 = out(1, nequi, 1_000_000, 0);
        Transaction entrada1 = in(2, davivienda, 1_000_000, 5);
        Transaction salida2 = out(3, nequi, 1_000_000, 600);   // sugerida, sin confirmar
        Transaction entrada2 = in(4, nu, 1_000_000, 605);
        Transaction salario = in(5, davivienda, 3_000_000, 1000);
        Transaction mercado = out(6, nu, 500_000, 1200);
        transactions.addAll(List.of(salida1, entrada1, salida2, entrada2, salario, mercado));
        confirmar(List.of(salida1), List.of(entrada1));

        MoneyFlowSummary s = service.summarize(USER);

        assertThat(s.grossIncome()).isEqualByComparingTo("5000000");
        assertThat(s.internalTransfers()).isEqualByComparingTo("1000000");
        assertThat(s.realIncome()).isEqualByComparingTo("4000000");   // la sugerida aun cuenta
        assertThat(s.realExpense()).isEqualByComparingTo("1500000");
        assertThat(s.confirmedTransfers()).isEqualTo(1);
        assertThat(s.pendingSuggestions()).isEqualTo(1);
        assertThat(s.pendingAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void laComisionDeUnaTransferenciaEsGastoReal() {
        Transaction salida = out(1, nequi, 1_000_000, 0);
        Transaction entrada = in(2, davivienda, 995_000, 10);
        transactions.addAll(List.of(salida, entrada));
        confirmar(List.of(salida), List.of(entrada));

        MoneyFlowSummary s = service.summarize(USER);

        assertThat(s.realIncome()).isEqualByComparingTo("0");
        assertThat(s.realExpense()).isEqualByComparingTo("5000");
        assertThat(s.transferFees()).isEqualByComparingTo("5000");
        assertThat(s.internalTransfers()).isEqualByComparingTo("995000");
    }

    @Test
    void unoAVariosNoCuentaComoIngresoNiGasto() {
        Transaction salida = out(1, nequi, 1_000_000, 0);
        Transaction a = in(2, davivienda, 600_000, 5);
        Transaction b = in(3, nu, 400_000, 5);
        transactions.addAll(List.of(salida, a, b));
        confirmar(List.of(salida), List.of(a, b));

        MoneyFlowSummary s = service.summarize(USER);

        assertThat(s.realIncome()).isEqualByComparingTo("0");
        assertThat(s.realExpense()).isEqualByComparingTo("0");
        assertThat(s.internalTransfers()).isEqualByComparingTo("1000000");
        assertThat(s.confirmedTransfers()).isEqualTo(1);
    }

    @Test
    void unaTransferenciaDeshechaVuelveAContarComoDineroReal() {
        Transaction salida = out(1, nequi, 200_000, 0);
        Transaction entrada = in(2, nu, 200_000, 5);
        transactions.addAll(List.of(salida, entrada));
        confirmar(List.of(salida), List.of(entrada));
        matches.forEach(ReconciliationMatch::undo);

        MoneyFlowSummary s = service.summarize(USER);

        assertThat(s.realIncome()).isEqualByComparingTo("200000");
        assertThat(s.confirmedTransfers()).isZero();
        assertThat(s.pendingSuggestions()).isEqualTo(1); // vuelve a ser sugerencia
    }

    @Test
    void elResumenSePuedeFiltrarPorPeriodo() {
        transactions.add(in(1, nequi, 100_000, 0));                // 10 sep
        transactions.add(in(2, nequi, 300_000, 60 * 24 * 30));      // 10 oct

        MoneyFlowSummary s = service.summarize(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(s.realIncome()).isEqualByComparingTo("300000");
    }
}
