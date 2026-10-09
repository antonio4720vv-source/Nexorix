package com.nexorix.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** La ultima categoria que la persona uso con ese mismo comercio: asi la IA "la va conociendo". */
    Optional<PurchaseNote> findFirstByUserIdAndDescriptionIgnoreCaseAndCategoryNotNullOrderByIdDesc(
            Long userId, String description);

    /** Categorias de la persona con cuantas compras y cuanto dinero tiene cada una. */
    @Query("select n.category, count(n), sum(n.amount) from PurchaseNote n "
            + "where n.user.id = :userId and n.category is not null group by n.category order by count(n) desc")
    List<Object[]> categoryTotals(@Param("userId") Long userId);

    /** Preguntas mandadas hoy (limite diario para no llenar el WhatsApp). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
