package com.nexorix.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    /**
     * Politica de contenido: el navegador solo carga scripts, estilos e
     * imagenes de Nexorix (y las letras de Google Fonts). Si alguien logra
     * inyectar un enlace a un script de otro sitio, el navegador lo bloquea.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "manifest-src 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "object-src 'none'"
    );

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Nexorix maneja su propio inicio de sesion (AuthController). Este bean
     * vacio evita que Spring cree un usuario de prueba y muestre en la
     * consola "Using generated security password".
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${nexorix.public-url:http://localhost:8080}") String publicUrl,
            @Value("${nexorix.security.ip-limits:true}") boolean ipLimits
    ) throws Exception {

        http
                // Limites por IP, bloqueo de solicitudes de otros sitios y cabeceras extra.
                .addFilterBefore(new AbuseProtectionFilter(publicUrl, ipLimits), UsernamePasswordAuthenticationFilter.class)

                // CSRF: la cookie de sesion es SameSite=Lax y AbuseProtectionFilter rechaza
                // solicitudes que cambian datos desde otros sitios (Origin / Sec-Fetch-Site).
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Publico: registro, inicio de sesion, recuperacion y webhook.
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/recovery/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/kyc/webhook").permitAll()
                        // Webhook de WhatsApp: GET para que Meta lo confirme, POST firmado con los mensajes.
                        .requestMatchers("/api/whatsapp/webhook").permitAll()
                        .requestMatchers("/api/users/logout").permitAll()
                        // Todo lo demas de la API exige sesion iniciada.
                        .requestMatchers("/api/**").authenticated()
                        // Paginas, estilos e iconos.
                        .anyRequest().permitAll()
                )

                // Sin sesion, la API responde 401 (y no una pagina de login).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // Cabeceras de seguridad del navegador.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                // Cerrar sesion de verdad: invalida la sesion y borra la cookie.
                .logout(logout -> logout
                        .logoutUrl("/api/users/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(200)
                        )
                );

        return http.build();
    }
}
