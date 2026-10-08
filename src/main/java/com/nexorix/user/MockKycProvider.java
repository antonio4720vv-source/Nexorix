package com.nexorix.user;

import org.springframework.stereotype.Component;

@Component
public class MockKycProvider implements KycProvider {

    @Override
    public KycProviderResponse startVerification(
            String documentType,
            String documentNumber,
            String userReference
    ) {

        return new KycProviderResponse(
                "MOCK_PROVIDER",
                "IN_PROGRESS",
                "MOCK-" + userReference,
                "Verificación iniciada correctamente en modo de prueba.",
                null
        );
    }
}