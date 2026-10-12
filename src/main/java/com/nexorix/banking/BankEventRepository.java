package com.nexorix.banking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BankEventRepository extends JpaRepository<BankEvent, Long> {

    boolean existsByEventId(String eventId);

    /**
     * Para detectar el mismo movimiento avisado dos veces (el banco repite la notificacion o Android la
     * vuelve a publicar): mismo banco, sentido, monto y comercio/persona, llegado hace muy poco.
     */
    boolean existsByUserIdAndBankAndDirectionAndAmountAndLabelIgnoreCaseAndCreatedAtAfter(
            Long userId, String bank, String direction, BigDecimal amount, String label, LocalDateTime since);

    Optional<BankEvent> findByIdAndUserId(Long id, Long userId);

    List<BankEvent> findByUserIdOrderByOccurredAtDescIdDesc(Long userId, Pageable page);
}
