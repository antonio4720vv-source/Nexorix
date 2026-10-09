package com.nexorix.fraud;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    Optional<AppNotification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    /** Avisos nuevos que todavia no salieron como notificacion del dispositivo. */
    @org.springframework.data.jpa.repository.Query(
            "select n from AppNotification n join fetch n.user where n.pushed = false and n.createdAt > :since order by n.id")
    List<AppNotification> findPendingPush(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
