package com.nexorix.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByCedula(String cedula);

    Optional<User> findByPublicId(String publicId);

    /** Buscador de amigos: usuarios activos cuyo nombre de usuario contiene el texto. */
    java.util.List<User> findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc(String text);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByCedula(String cedula);
}