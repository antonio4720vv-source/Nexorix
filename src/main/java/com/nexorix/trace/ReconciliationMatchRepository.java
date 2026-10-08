package com.nexorix.trace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {

    List<ReconciliationMatch> findByOriginTransactionId(Long transactionId);

    List<ReconciliationMatch> findByDestinationTransactionId(Long transactionId);

    /** Todas las conciliaciones de la persona (activas y deshechas), las mas nuevas primero. */
    List<ReconciliationMatch> findByOriginTransactionAccountUserUsernameOrderByCreatedAtDesc(String username);

    List<ReconciliationMatch> findByGroupId(String groupId);

    /** ¿Alguno de estos movimientos ya esta en una conciliacion activa? */
    boolean existsByStatusAndOriginTransactionIdIn(ReconciliationStatus status, Collection<Long> ids);

    boolean existsByStatusAndDestinationTransactionIdIn(ReconciliationStatus status, Collection<Long> ids);
}
