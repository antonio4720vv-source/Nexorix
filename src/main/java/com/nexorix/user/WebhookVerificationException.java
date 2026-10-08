package com.nexorix.user;

/**
 * Se lanza cuando un webhook no supera la verificacion de seguridad:
 * falta la firma, la firma no coincide o el timestamp es demasiado viejo.
 */
public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException(String message) {
        super(message);
    }
}
