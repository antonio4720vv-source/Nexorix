package com.nexorix.dto;

/** Primer paso del inicio de sesion: usuario y contrasena. */
public record LoginRequest(
        String username,
        String password
) {
}
