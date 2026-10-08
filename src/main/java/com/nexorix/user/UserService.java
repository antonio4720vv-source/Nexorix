package com.nexorix.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private static final int MIN_PASSWORD = 10;
    private static final int MAX_PASSWORD = 72; // BCrypt ignora lo que pase de 72

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea la cuenta con contrasena. El PIN se crea despues,
     * cuando la persona ya verifico su identidad.
     */
    @Transactional
    public User createUser(
            String name,
            String username,
            String email,
            String cedula,
            String password
    ) {

        String cleanName = required(name, "El nombre es obligatorio.");
        String cleanUsername = required(username, "El usuario es obligatorio.");
        String cleanEmail = required(email, "El correo es obligatorio.").toLowerCase(Locale.ROOT);
        String cleanCedula = required(cedula, "El número de documento es obligatorio.");

        if (!cleanUsername.matches("[A-Za-z0-9._-]{4,30}")) {
            throw new IllegalArgumentException(
                    "El usuario debe tener entre 4 y 30 caracteres: letras, números, punto, guion o guion bajo."
            );
        }

        validatePassword(password, cleanUsername);

        if (userRepository.findByUsername(cleanUsername).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está registrado.");
        }

        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado.");
        }

        if (userRepository.findByCedula(cleanCedula).isPresent()) {
            throw new IllegalArgumentException("La cédula ya está registrada.");
        }

        User user = new User(
                cleanName,
                cleanUsername,
                cleanEmail,
                cleanCedula,
                passwordEncoder.encode(password)
        );

        return userRepository.save(user);
    }

    /**
     * Contrasena profesional:
     * - entre 10 y 72 caracteres
     * - al menos una mayuscula, una minuscula, un numero y un caracter especial
     * - sin espacios
     * - que no contenga el nombre de usuario
     */
    public static void validatePassword(String password, String username) {

        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.");
        }

        if (password.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contraseña no puede tener más de " + MAX_PASSWORD + " caracteres.");
        }

        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La contraseña no puede tener espacios.");
        }

        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra mayúscula.");
        }

        if (password.chars().noneMatch(Character::isLowerCase)) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra minúscula.");
        }

        if (password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contraseña debe tener al menos un número.");
        }

        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos un carácter especial, por ejemplo ! @ # $ % * ?");
        }

        if (username != null && username.length() >= 3
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("La contraseña no puede contener tu nombre de usuario.");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
