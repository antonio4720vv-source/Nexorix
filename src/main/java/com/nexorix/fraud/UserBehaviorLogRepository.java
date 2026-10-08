package com.nexorix.fraud;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserBehaviorLogRepository extends JpaRepository<UserBehaviorLog, Long> {

    long countByUserId(Long userId);

    boolean existsByUserIdAndMerchantKey(Long userId, String merchantKey);

    boolean existsByUserIdAndRecipientKey(Long userId, String recipientKey);

    /** Ultima operacion CON ubicacion anterior a un momento: punto de partida de la regla de imposibilidad fisica. */
    Optional<UserBehaviorLog> findFirstByUserIdAndLatitudeNotNullAndOccurredAtBeforeOrderByOccurredAtDesc(
            Long userId, LocalDateTime before);

    @Query("select l.merchantName, count(l) from UserBehaviorLog l where l.user.id = :userId and l.merchantKey is not null "
            + "group by l.merchantKey, l.merchantName order by count(l) desc")
    List<Object[]> topMerchants(@Param("userId") Long userId, Pageable page);

    @Query("select l.category, count(l) from UserBehaviorLog l where l.user.id = :userId and l.category is not null "
            + "group by l.category order by count(l) desc")
    List<Object[]> topCategories(@Param("userId") Long userId, Pageable page);

    @Query("select l.recipientName, count(l) from UserBehaviorLog l where l.user.id = :userId and l.recipientKey is not null "
            + "group by l.recipientKey, l.recipientName order by count(l) desc")
    List<Object[]> topRecipients(@Param("userId") Long userId, Pageable page);

    @Query("select l.city, l.country, count(l) from UserBehaviorLog l where l.user.id = :userId and l.latitude is not null "
            + "group by l.city, l.country order by count(l) desc")
    List<Object[]> topPlaces(@Param("userId") Long userId, Pageable page);
}
