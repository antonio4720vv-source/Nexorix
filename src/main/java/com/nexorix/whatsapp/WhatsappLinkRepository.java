package com.nexorix.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsappLinkRepository extends JpaRepository<WhatsappLink, Long> {

    Optional<WhatsappLink> findByUserId(Long userId);

    Optional<WhatsappLink> findByPhone(String phone);
}
