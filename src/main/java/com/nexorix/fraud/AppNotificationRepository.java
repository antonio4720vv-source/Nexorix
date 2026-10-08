package com.nexorix.fraud;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    Optional<AppNotification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);
}
