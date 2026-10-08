package com.nexorix.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryRequestRepository extends JpaRepository<RecoveryRequest, Long> {

    Optional<RecoveryRequest> findByDiditSessionId(String diditSessionId);

    List<RecoveryRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
