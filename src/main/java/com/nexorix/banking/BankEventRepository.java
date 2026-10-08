package com.nexorix.banking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankEventRepository extends JpaRepository<BankEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<BankEvent> findByIdAndUserId(Long id, Long userId);

    List<BankEvent> findByUserIdOrderByOccurredAtDescIdDesc(Long userId, Pageable page);
}
