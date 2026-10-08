package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    private static final String CSV = """
            fecha,descripcion,valor
            2026-09-01,Salario septiembre,3000000
            2026-09-02,Compra Exito,-150000
            2026-09-02,Cafe Juan Valdez,-9000
            2026-09-02,Cafe Juan Valdez,-9000
            fecha mala,Algo,1000
            """;

    private ImportBatchRepository batchRepository;
    private ImportRowRepository rowRepository;
    private TransactionRepository transactionRepository;
    private TransactionService transactionService;
    private ImportService service;
    private Account nequi;
    private final List<ImportRow> savedRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        batchRepository = mock(ImportBatchRepository.class);
        rowRepository = mock(ImportRowRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        transactionService = mock(TransactionService.class);

        service = new ImportService(batchRepository, rowRepository, accountRepository,
                userRepository, transactionRepository, transactionService);

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        User otra = new User("Otra", "otra", "otra@nexorix.com", "2", "hash");
        ReflectionTestUtils.setField(otra, "id", 2L);

        nequi = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        Account ajena = new Account("Ajena", "AHORROS", "Nu", BigDecimal.ZERO, otra);
        ReflectionTestUtils.setField(ajena, "id", 9L);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(nequi));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(ajena));
        when(batchRepository.findFirstByAccountIdAndFileHashAndStatus(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch batch = call.getArgument(0);
            if (batch.getId() == null) {
                ReflectionTestUtils.setField(batch, "id", 50L);
            }
            return batch;
        });

        AtomicLong ids = new AtomicLong(100);
        when(rowRepository.saveAll(anyList())).thenAnswer(call -> {
            List<ImportRow> rows = call.getArgument(0);
            rows.forEach(row -> ReflectionTestUtils.setField(row, "id", ids.getAndIncrement()));
            savedRows.addAll(rows);
            return rows;
        });
        when(rowRepository.findByBatchIdOrderByLineNumberAsc(50L)).thenAnswer(call -> savedRows);
    }

    private ImportPreview previewCsv() {
        return service.preview("ana", 6L, "septiembre.csv", CSV.getBytes(StandardCharsets.UTF_8), null);
    }

    @Test
    void laVistaPreviaClasificaYCuentaLasFilas() {
        ImportPreview preview = previewCsv();

        assertThat(preview.total()).isEqualTo(5);
        assertThat(preview.newRows()).isEqualTo(4);
        assertThat(preview.invalid()).isEqualTo(1);
        assertThat(preview.incomeTotal()).isEqualByComparingTo("3000000");
        assertThat(preview.expenseTotal()).isEqualByComparingTo("168000");
        assertThat(preview.rows().get(0).category()).isEqualTo("SALARIO");
        assertThat(preview.rows().get(1).category()).isEqualTo("MERCADO");
    }

    @Test
    void dosFilasIgualesEnElArchivoSeAvisanPeroNoSeDescartan() {
        ImportPreview preview = previewCsv();

        ImportPreview.Row second = preview.rows().get(3);
        assertThat(second.status()).isEqualTo(ImportRow.NEW);
        assertThat(second.selected()).isTrue();
        assertThat(second.message()).contains("Igual a la línea");
    }

    @Test
    void unMovimientoQueYaExisteSeMarcaComoDuplicado() {
        Transaction existente = new Transaction(new BigDecimal("150000"), "EGRESO", "Mercado",
                LocalDateTime.of(2026, 9, 2, 18, 30), nequi, null);
        when(transactionRepository.findByAccountIdAndTransactionDateBetween(eq(6L), any(), any()))
                .thenReturn(List.of(existente));

        ImportPreview preview = previewCsv();

        ImportPreview.Row compra = preview.rows().get(1);
        assertThat(compra.status()).isEqualTo(ImportRow.DUPLICATE);
        assertThat(compra.selected()).isFalse();
        assertThat(compra.message()).contains("Mercado");
        assertThat(preview.duplicates()).isEqualTo(1);
    }

    @Test
    void noSePuedeImportarDosVecesElMismoArchivo() {
        ImportBatch anterior = new ImportBatch(null, nequi, "septiembre.csv", "x", "CSV");
        anterior.confirm(4);
        when(batchRepository.findFirstByAccountIdAndFileHashAndStatus(eq(6L), anyString(), eq(ImportBatch.CONFIRMED)))
                .thenReturn(Optional.of(anterior));

        assertThatThrownBy(this::previewCsv)
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("Ya importaste");
    }

    @Test
    void noSePuedeImportarEnLaCuentaDeOtraPersona() {
        assertThatThrownBy(() -> service.preview("ana", 9L, "x.csv", CSV.getBytes(), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void soloSeAceptanPdfYCsv() {
        assertThatThrownBy(() -> service.preview("ana", 6L, "foto.jpg", new byte[]{1, 2, 3}, null))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("PDF o CSV");
    }

    @Test
    void confirmarCreaSoloLasFilasElegidasYValidas() {
        ImportPreview preview = previewCsv();
        ImportBatch batch = (ImportBatch) ReflectionTestUtils.getField(savedRows.get(0), "batch");
        when(batchRepository.findById(50L)).thenReturn(Optional.of(batch));
        when(transactionService.saveImported(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> new Transaction(call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4), nequi, null));

        Long salario = preview.rows().get(0).id();
        Long invalida = preview.rows().get(4).id();

        int imported = service.confirm("ana", 50L, List.of(salario, invalida));

        assertThat(imported).isEqualTo(1);
        verify(transactionService, times(1)).saveImported(eq(nequi), any(), eq("INGRESO"),
                eq("Salario septiembre"), any(), any(), eq("SALARIO"));
        assertThat(batch.getStatus()).isEqualTo(ImportBatch.CONFIRMED);
    }

    @Test
    void otraPersonaNoPuedeConfirmarMiImportacion() {
        previewCsv();
        ImportBatch batch = (ImportBatch) ReflectionTestUtils.getField(savedRows.get(0), "batch");
        when(batchRepository.findById(50L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.confirm("otra", 50L, List.of(100L)))
                .isInstanceOf(ImportException.class);
        verify(transactionService, never()).saveImported(any(), any(), any(), any(), any(), any(), any());
    }
}
