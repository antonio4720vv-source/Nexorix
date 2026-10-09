package com.nexorix.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface BannedIpRepository extends JpaRepository<BannedIp, String> {

    List<BannedIp> findByBannedUntilAfter(LocalDateTime now);

    @Transactional
    @Modifying
    @Query("delete from BannedIp b where b.bannedUntil < :now")
    int deleteExpired(LocalDateTime now);
}
