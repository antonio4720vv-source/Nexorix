package com.nexorix.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseColumnRepository extends JpaRepository<PurchaseColumn, Long> {

    List<PurchaseColumn> findByUserIdOrderByPositionAscIdAsc(Long userId);

    Optional<PurchaseColumn> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndKey(Long userId, String key);

    long countByUserId(Long userId);
}
