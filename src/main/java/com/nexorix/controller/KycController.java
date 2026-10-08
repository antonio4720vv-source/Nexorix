package com.nexorix.controller;

import com.nexorix.user.KycService;
import com.nexorix.user.KycVerification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/start")
    public ResponseEntity<KycResponse> startVerification(
            @RequestParam String documentType,
            @RequestParam String documentNumber
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        KycVerification verification =
                kycService.startVerification(
                        username,
                        documentType,
                        documentNumber
                );

        return ResponseEntity.ok(
                KycResponse.fromVerification(verification)
        );
    }

    @GetMapping("/status")
    public ResponseEntity<KycResponse> getStatus() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        KycVerification verification =
                kycService.getLatestVerification(username);

        if (verification == null) {
            return ResponseEntity.ok(null);
        }

        return ResponseEntity.ok(
                KycResponse.fromVerification(verification)
        );
    }

    public static class KycResponse {

        private final Long id;
        private final String documentType;
        private final String status;
        private final String provider;
        private final String verificationReference;
        private final String verificationUrl;
        private final String resultMessage;
        private final LocalDateTime startedAt;
        private final LocalDateTime completedAt;

        public KycResponse(
                Long id,
                String documentType,
                String status,
                String provider,
                String verificationReference,
                String verificationUrl,
                String resultMessage,
                LocalDateTime startedAt,
                LocalDateTime completedAt
        ) {
            this.id = id;
            this.documentType = documentType;
            this.status = status;
            this.provider = provider;
            this.verificationReference = verificationReference;
            this.verificationUrl = verificationUrl;
            this.resultMessage = resultMessage;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
        }

        public static KycResponse fromVerification(
                KycVerification verification
        ) {
            return new KycResponse(
                    verification.getId(),
                    verification.getDocumentType(),
                    verification.getStatus().name(),
                    verification.getProvider(),
                    verification.getVerificationReference(),
                    verification.getVerificationUrl(),
                    verification.getResultMessage(),
                    verification.getStartedAt(),
                    verification.getCompletedAt()
            );
        }

        public Long getId() {
            return id;
        }

        public String getDocumentType() {
            return documentType;
        }

        public String getStatus() {
            return status;
        }

        public String getProvider() {
            return provider;
        }

        public String getVerificationReference() {
            return verificationReference;
        }

        public String getVerificationUrl() {
            return verificationUrl;
        }

        public String getResultMessage() {
            return resultMessage;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(409)
                .body(exception.getMessage());
    }
}