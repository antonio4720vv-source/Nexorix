package com.nexorix.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByUserIdOrderByReceivedAtDescIdDesc(Long userId, Pageable page);

    @Modifying
    @Query("delete from NotificationLog n where n.receivedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
