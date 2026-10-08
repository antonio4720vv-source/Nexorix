package com.nexorix.dto;

/** PIN de 6 digitos. pinConfirm solo se usa al CREAR el PIN. */
public record PinRequest(
        String pin,
        String pinConfirm
) {
}
