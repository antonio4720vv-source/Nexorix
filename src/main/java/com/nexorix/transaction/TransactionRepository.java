package com.nexorix.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountUserUsernameOrderByTransactionDateDesc(String username);

    /** Movimientos de una cuenta en un rango de fechas (para detectar duplicados). */
    List<Transaction> findByAccountIdAndTransactionDateBetween(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to
    );

    /** Movimientos de la persona en un periodo (reportes y agente). */
    List<Transaction> findByAccountUserUsernameAndTransactionDateBetweenOrderByTransactionDateDesc(
            String username,
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * Bloquea los movimientos mientras se confirma una conciliacion, para que dos
     * confirmaciones al mismo tiempo no usen el mismo movimiento dos veces.
     * Ordenados por id para evitar bloqueos cruzados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id in :ids order by t.id")
    List<Transaction> lockByIds(@Param("ids") Collection<Long> ids);
}
