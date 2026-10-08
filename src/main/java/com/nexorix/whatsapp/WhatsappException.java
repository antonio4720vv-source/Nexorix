package com.nexorix.whatsapp;

/** Error al hablar con WhatsApp, con un mensaje entendible para la persona. */
public class WhatsappException extends RuntimeException {

    public WhatsappException(String message) {
        super(message);
    }

    public WhatsappException(String message, Throwable cause) {
        super(message, cause);
    }
}
