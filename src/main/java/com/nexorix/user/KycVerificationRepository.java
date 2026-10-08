package com.nexorix.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycVerificationRepository
        extends JpaRepository<KycVerification, Long> {

    List<KycVerification> findByUserUsernameOrderByStartedAtDesc(
            String username
    );

    Optional<KycVerification> findFirstByUserUsernameOrderByStartedAtDesc(
            String username
    );

    Optional<KycVerification> findByVerificationReference(
            String verificationReference
    );
}