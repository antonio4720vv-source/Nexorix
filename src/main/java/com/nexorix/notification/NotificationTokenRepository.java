package com.nexorix.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    Optional<NotificationToken> findByTokenHash(String tokenHash);

    Optional<NotificationToken> findByUserUsername(String username);
}
