package com.nexorix.auth;

/**
 * Error en la recuperacion de la cuenta.
 * restart = true cuando la persona debe empezar de nuevo.
 */
public class RecoveryException extends RuntimeException {

    private final boolean restart;

    public RecoveryException(String message, boolean restart) {
        super(message);
        this.restart = restart;
    }

    public boolean isRestart() {
        return restart;
    }
}
