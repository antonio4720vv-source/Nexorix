package com.nexorix.controller;

import com.nexorix.auth.AuthService;
import com.nexorix.auth.SessionLogin;
import com.nexorix.dto.CreateUserRequest;
import com.nexorix.dto.PinRequest;
import com.nexorix.dto.UserResponse;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final SessionLogin sessionLogin;

    public UserController(
            UserService userService,
            UserRepository userRepository,
            AuthService authService,
            SessionLogin sessionLogin
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.sessionLogin = sessionLogin;
    }

    /**
     * Registro. Deja la sesion iniciada para que la persona pueda
     * verificar su identidad de inmediato.
     */
    @PostMapping
    public UserResponse createUser(
            @RequestBody CreateUserRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user = userService.createUser(
                body.name(),
                body.username(),
                body.email(),
                body.cedula(),
                body.password()
        );

        sessionLogin.logIn(user.getUsername(), request, response);

        return UserResponse.fromUser(user);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return currentUser()
                .map(UserResponse::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** Datos para la ventana de perfil: lo sensible va censurado desde el servidor. */
    @GetMapping("/me/profile")
    public ResponseEntity<com.nexorix.dto.ProfileResponse> profile() {
        return currentUser()
                .map(com.nexorix.dto.ProfileResponse::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    public record PhotoRequest(String image) {
    }

    private static final java.util.regex.Pattern PHOTO =
            java.util.regex.Pattern.compile("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$");

    /** Guarda la foto de perfil (el navegador ya la reduce; aqui se limita el tipo y el peso). */
    @PostMapping("/me/photo")
    public ResponseEntity<?> savePhoto(@RequestBody PhotoRequest body) {
        Optional<User> user = currentUser();
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        String image = body == null ? null : body.image();
        if (image == null || image.length() > 300_000 || !PHOTO.matcher(image).matches()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "La foto debe ser JPG, PNG o WEBP y pesar poco."));
        }
        user.get().setPhoto(image);
        userRepository.save(user.get());
        return ResponseEntity.ok(java.util.Map.of("ok", true));
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<?> deletePhoto() {
        Optional<User> user = currentUser();
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        user.get().setPhoto(null);
        userRepository.save(user.get());
        return ResponseEntity.ok(java.util.Map.of("ok", true));
    }

    /** Crear el PIN justo despues de verificar la identidad (la primera vez). */
    @PostMapping("/me/pin")
    public ResponseEntity<Map<String, Object>> createPin(@RequestBody PinRequest body) {

        User user = currentUser().orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Inicia sesión para continuar."));
        }

        try {
            authService.createPin(user.getId(), body.pin(), body.pinConfirm());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Optional<User> currentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }

        return userRepository.findByUsername(authentication.getName());
    }
}
