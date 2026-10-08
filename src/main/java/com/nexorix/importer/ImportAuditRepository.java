package com.nexorix.importer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportAuditRepository extends JpaRepository<ImportAuditEvent, Long> {

    /** Lo mas reciente primero. */
    List<ImportAuditEvent> findByUserIdOrderByIdDesc(Long userId, Pageable page);

    List<ImportAuditEvent> findByUserIdAndBatchIdOrderByIdAsc(Long userId, Long batchId);
}
