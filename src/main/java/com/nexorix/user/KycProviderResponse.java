package com.nexorix.user;

public class KycProviderResponse {

    private final String provider;
    private final String status;
    private final String verificationReference;
    private final String resultMessage;
    private final String verificationUrl;

    public KycProviderResponse(
            String provider,
            String status,
            String verificationReference,
            String resultMessage,
            String verificationUrl
    ) {
        this.provider = provider;
        this.status = status;
        this.verificationReference = verificationReference;
        this.resultMessage = resultMessage;
        this.verificationUrl = verificationUrl;
    }

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public String getVerificationReference() {
        return verificationReference;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }
}