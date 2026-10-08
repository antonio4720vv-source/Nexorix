package com.nexorix.split;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SplitExpenseRepository extends JpaRepository<SplitExpense, Long> {

    List<SplitExpense> findByPayerIdOrderByIdDesc(Long payerId, Pageable page);

    Optional<SplitExpense> findByIdAndPayerId(Long id, Long payerId);
}
