package com.nexorix.auth;

/** La cuenta esta bloqueada temporalmente por demasiados intentos fallidos. */
public class AccountLockedException extends AuthException {

    public AccountLockedException(String message) {
        super(message);
    }
}
