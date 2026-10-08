package com.nexorix.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Ab1!corta,           al menos 10",
            "nexorix#2026,        mayúscula",
            "NEXORIX#2026,        minúscula",
            "Nexorix#Clave,       número",
            "Nexorix2026Abc,      carácter especial",
            "'Nexorix #2026',     espacios",
            "Anaprueba7#Ok,       nombre de usuario"
    })
    void rechazaContrasenasQueNoCumplenLasReglas(String password, String motivo) {
        assertThatThrownBy(() -> UserService.validatePassword(password, "anaprueba7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(motivo.trim());
    }

    @Test
    void aceptaUnaContrasenaProfesional() {
        assertThatCode(() -> UserService.validatePassword("Nexorix#2026", "anaprueba7"))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaContrasenasDeMasDe72Caracteres() {
        assertThatThrownBy(() -> UserService.validatePassword("Aa1!".repeat(20), "ana"))
                .hasMessageContaining("72");
    }
}
