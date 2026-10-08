package com.nexorix.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}
