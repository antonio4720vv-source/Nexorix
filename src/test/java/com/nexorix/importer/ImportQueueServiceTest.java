package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportQueueServiceTest {

    private ImportBatchRepository batchRepository;
    private TaskExecutor executor;
    private ImportQueueService service;
    private final List<Runnable> queued = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        batchRepository = mock(ImportBatchRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        executor = queued::add;

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        Account nequi = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(nequi));
        when(batchRepository.findFirstByAccountIdAndFileHashAndStatus(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(call -> {
            ImportBatch b = call.getArgument(0);
            if (b.getId() == null) ReflectionTestUtils.setField(b, "id", 100L + queued.size());
            return b;
        });

        service = build(executor);
    }

    private ImportQueueService build(TaskExecutor exec) throws Exception {
        UserRepository users = mock(UserRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        Account nequi = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accounts.findById(6L)).thenReturn(Optional.of(nequi));
        return new ImportQueueService(batchRepository, mock(ImportRowRepository.class), users, accounts,
                mock(ImportProcessor.class), exec, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                10, 20);
    }

    private static ImportQueueService.Incoming csv(String name, String extra) {
        return new ImportQueueService.Incoming(name,
                ("fecha;descripcion;valor\n2026-09-01;Salario " + extra + ";3000000\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void variosArchivosQuedanEnColaYLaRespuestaEsInmediata() {
        List<ImportStatus> result = service.enqueue("ana", 6L,
                List.of(csv("a.csv", "1"), csv("b.csv", "2"), csv("c.csv", "3")), null);

        assertThat(result).hasSize(3).allMatch(s -> ImportBatch.QUEUED.equals(s.status()));
        assertThat(queued).hasSize(3); // se procesaran en segundo plano
    }

    @Test
    void unArchivoRepetidoEnLaMismaSeleccionSeRechaza() {
        List<ImportStatus> result = service.enqueue("ana", 6L,
                List.of(csv("a.csv", "1"), csv("copia.csv", "1")), null);

        assertThat(result.get(0).status()).isEqualTo(ImportBatch.QUEUED);
        assertThat(result.get(1).status()).isEqualTo(ImportBatch.FAILED);
        assertThat(result.get(1).errorMessage()).contains("repetido");
    }

    @Test
    void unFormatoNoValidoSeRechazaSinAfectarALosDemas() {
        List<ImportStatus> result = service.enqueue("ana", 6L, List.of(
                csv("a.csv", "1"), new ImportQueueService.Incoming("foto.jpg", new byte[]{1, 2, 3})), null);

        assertThat(result.get(0).status()).isEqualTo(ImportBatch.QUEUED);
        assertThat(result.get(1).errorMessage()).contains("PDF o CSV");
    }

    @Test
    void maximoDiezArchivosPorEnvio() {
        List<ImportQueueService.Incoming> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) files.add(csv(i + ".csv", String.valueOf(i)));

        assertThatThrownBy(() -> service.enqueue("ana", 6L, files, null))
                .hasMessageContaining("máximo 10");
    }

    @Test
    void unaPersonaNoPuedeLlenarLaColaSola() {
        when(batchRepository.countByUserIdAndStatusIn(anyLong(), any())).thenReturn(19L);

        assertThatThrownBy(() -> service.enqueue("ana", 6L, List.of(csv("a.csv", "1"), csv("b.csv", "2")), null))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("en proceso");
    }

    @Test
    void siLaColaGlobalEstaLlenaSeAvisaEnVezDeCaerse() throws Exception {
        TaskExecutor full = mock(TaskExecutor.class);
        doThrow(new TaskRejectedException("llena")).when(full).execute(any());
        when(batchRepository.findById(anyLong())).thenReturn(Optional.empty());

        List<ImportStatus> result = build(full).enqueue("ana", 6L, List.of(csv("a.csv", "1")), null);

        assertThat(result.get(0).status()).isEqualTo(ImportBatch.FAILED);
        assertThat(result.get(0).errorMessage()).contains("Intenta en un minuto");
    }
}
