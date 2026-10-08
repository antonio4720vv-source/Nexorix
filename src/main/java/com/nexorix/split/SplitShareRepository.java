package com.nexorix.split;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SplitShareRepository extends JpaRepository<SplitShare, Long> {

    @Query("select s from SplitShare s join fetch s.participant where s.expense.id in :expenseIds order by s.id")
    List<SplitShare> findByExpenseIds(@Param("expenseIds") Collection<Long> expenseIds);

    /** Lo que la persona debe (de cuentas que no se cancelaron). */
    @Query("select s from SplitShare s join fetch s.expense e join fetch e.payer "
            + "where s.participant.id = :userId and e.status = 'ABIERTA' order by s.id desc")
    List<SplitShare> findOwedBy(@Param("userId") Long userId, Pageable page);

    @Query("select s from SplitShare s join fetch s.expense e join fetch e.payer join fetch s.participant where s.token = :token")
    Optional<SplitShare> findByToken(@Param("token") String token);

    @Query("select s from SplitShare s join fetch s.expense e join fetch e.payer join fetch s.participant where s.id = :id")
    Optional<SplitShare> findWithExpense(@Param("id") Long id);
}
