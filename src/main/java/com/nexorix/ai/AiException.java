package com.nexorix.ai;

/** Error al usar la IA (no configurada, ocupada o respuesta invalida). */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
