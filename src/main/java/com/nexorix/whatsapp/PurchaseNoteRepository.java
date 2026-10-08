package com.nexorix.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseNoteRepository extends JpaRepository<PurchaseNote, Long> {

    List<PurchaseNote> findByUserIdOrderByPurchaseDateDescIdDesc(Long userId);

    Optional<PurchaseNote> findByIdAndUserId(Long id, Long userId);

    /** La pregunta que la persona cito al responder. */
    Optional<PurchaseNote> findByQuestionMessageIdAndUserId(String questionMessageId, Long userId);

    /** La pregunta mas reciente sin responder (cuando la persona responde sin citar). */
    Optional<PurchaseNote> findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, PurchaseNoteStatus status, LocalDateTime after);

    /** Preguntas mandadas hoy (limite diario para no llenar el WhatsApp). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
