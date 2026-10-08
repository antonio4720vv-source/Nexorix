package com.nexorix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;

/** Abre la sesion de Spring Security para un usuario. */
@Component
public class SessionLogin {

    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public void logIn(String username, HttpServletRequest request, HttpServletResponse response) {

        // Cambia el id de la sesion al iniciar sesion: evita que alguien
        // que conocia el id anterior quede dentro (session fixation).
        request.getSession(true);
        request.changeSessionId();

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        username, null, Collections.emptyList());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
