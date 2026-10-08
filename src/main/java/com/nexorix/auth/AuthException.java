package com.nexorix.auth;

/** Error de inicio de sesion (datos incorrectos, paso equivocado, etc.). */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
