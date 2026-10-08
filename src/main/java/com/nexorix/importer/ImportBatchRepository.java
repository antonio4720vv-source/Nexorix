package com.nexorix.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    /** ¿Este mismo archivo ya se importo en esta cuenta? */
    Optional<ImportBatch> findFirstByAccountIdAndFileHashAndStatus(
            Long accountId, String fileHash, String status);

    /** Archivos de una persona que todavia se estan procesando. */
    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    List<ImportBatch> findByStatusIn(Collection<String> statuses);

    List<ImportBatch> findByStatusInAndCreatedAtBefore(Collection<String> statuses, LocalDateTime before);

    List<ImportBatch> findByIdInAndUserUsername(Collection<Long> ids, String username);
}
