package com.nexorix.user;

public interface KycProvider {

    KycProviderResponse startVerification(
            String documentType,
            String documentNumber,
            String userReference
    );
}