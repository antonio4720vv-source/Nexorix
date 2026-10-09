package com.nexorix.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    @Transactional
    @Modifying
    @Query("delete from SecurityEvent e where e.createdAt < :limit")
    int deleteOlderThan(LocalDateTime limit);
}
