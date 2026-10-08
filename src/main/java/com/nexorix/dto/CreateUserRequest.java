package com.nexorix.dto;

/** Datos para crear una cuenta. El PIN se crea despues de verificar la identidad. */
public record CreateUserRequest(
        String name,
        String username,
        String email,
        String cedula,
        String password
) {
}
