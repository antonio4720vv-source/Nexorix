package com.nexorix.user;

import com.nexorix.security.FieldCipher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIndex(String emailIndex);

    Optional<User> findByCedulaIndex(String cedulaIndex);

    /** Correo y cedula se guardan cifrados: se buscan por su huella (FieldCipher.blindIndex). */
    default Optional<User> findByEmail(String email) {
        return findByEmailIndex(FieldCipher.blindIndex(email));
    }

    default Optional<User> findByCedula(String cedula) {
        return findByCedulaIndex(FieldCipher.blindIndex(cedula));
    }

    Optional<User> findByPublicId(String publicId);

    /** Buscador de amigos: usuarios activos cuyo nombre de usuario contiene el texto. */
    java.util.List<User> findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc(String text);

    boolean existsByUsername(String username);

    boolean existsByEmailIndex(String emailIndex);

    boolean existsByCedulaIndex(String cedulaIndex);

    default boolean existsByEmail(String email) {
        return existsByEmailIndex(FieldCipher.blindIndex(email));
    }

    default boolean existsByCedula(String cedula) {
        return existsByCedulaIndex(FieldCipher.blindIndex(cedula));
    }
}