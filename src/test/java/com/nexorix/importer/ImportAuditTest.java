package com.nexorix.importer;

import com.nexorix.account.Account;
import com.nexorix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImportAuditTest {

    private ImportAuditRepository repository;
    private org.springframework.transaction.PlatformTransactionManager txManager;
    private ImportAudit audit;
    private ImportBatch batch;

    @BeforeEach
    void setUp() {
        repository = mock(ImportAuditRepository.class);
        txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        audit = new ImportAudit(repository, txManager);

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        Account nequi = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        batch = new ImportBatch(ana, nequi, "extracto.pdf", "a".repeat(64), "PDF");
        ReflectionTestUtils.setField(batch, "id", 77L);
    }

    @Test
    void guardaCadaEventoConElArchivoYSuHuella() {
        audit.record(batch, ImportAudit.READ, "12 movimientos");

        ArgumentCaptor<ImportAuditEvent> captor = ArgumentCaptor.forClass(ImportAuditEvent.class);
        verify(repository).save(captor.capture());
        ImportAuditEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAccountId()).isEqualTo(6L);
        assertThat(saved.getBatchId()).isEqualTo(77L);
        assertThat(saved.getFileName()).isEqualTo("extracto.pdf");
        assertThat(saved.getFileHash()).isEqualTo("a".repeat(64));
        assertThat(saved.getEvent()).isEqualTo("LEIDO");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void cadaAnotacionVaEnSuPropiaTransaccion() {
        // Regresion: con @Transactional sobre un metodo llamado desde la misma clase, los eventos se perdian.
        audit.record(batch, ImportAudit.READ, "x");

        ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definition =
                ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(txManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void despuesDeConfirmarTambienUsaSuPropiaTransaccion() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            audit.recordAfterCommit(batch, ImportAudit.CONFIRMED, "x");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(txManager).getTransaction(any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void unDetalleLargoSeRecorta() {
        ImportAuditEvent event = new ImportAuditEvent(1L, null, null, "x.csv", null, "ERROR", "x".repeat(900));
        assertThat(event.getDetail()).hasSize(500).endsWith("...");
    }

    @Test
    void siGuardarFallaLaImportacionNoSeCae() {
        org.mockito.Mockito.when(repository.save(any())).thenThrow(new IllegalStateException("base caida"));
        audit.record(batch, ImportAudit.FAILED, "x"); // no lanza
    }

    @Test
    void despuesDeLaTransaccionSoloAnotaSiSeConfirma() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            audit.recordAfterCommit(batch, ImportAudit.CONFIRMED, "40 movimientos");
            verify(repository, never()).save(any()); // todavia no se confirmo

            List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
            assertThat(pending).hasSize(1);
            pending.forEach(TransactionSynchronization::afterCommit);

            verify(repository).save(any(ImportAuditEvent.class));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void siLaTransaccionSeDeshaceNoQuedaNada() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            audit.recordAfterCommit(batch, ImportAudit.CONFIRMED, "40 movimientos");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(repository, never()).save(any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void fueraDeUnaTransaccionAnotaDeInmediato() {
        audit.recordAfterCommit(batch, ImportAudit.PROCESSING, "Empezó");
        verify(repository).save(any(ImportAuditEvent.class));
    }

    @Test
    void elLimiteDeLaListaSeRespeta() {
        audit.recent(1L, 100_000);
        verify(repository).findByUserIdOrderByIdDesc(1L, PageRequest.of(0, ImportAudit.MAX_LIST));
    }

    @Test
    void resumenDeIdsCreados() {
        assertThat(ImportService.idsSummary(List.of())).isEqualTo("ninguno");
        assertThat(ImportService.idsSummary(List.of(5L, 6L))).isEqualTo("5, 6");
        assertThat(ImportService.idsSummary(java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList()))
                .isEqualTo("1 … 40 (40)");
    }
}
