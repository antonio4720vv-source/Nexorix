package com.nexorix.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {

    List<ImportRow> findByBatchIdOrderByLineNumberAsc(Long batchId);

    void deleteByBatchId(Long batchId);
}
