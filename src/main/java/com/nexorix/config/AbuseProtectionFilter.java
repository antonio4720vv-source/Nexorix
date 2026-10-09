package com.nexorix.config;

import com.nexorix.ai.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Proteccion operativa (antes de llegar a los controladores):
 *
 * 1. Limite por IP en las rutas publicas sensibles (inicio de sesion, registro,
 *    recuperacion). Se suma al bloqueo por usuario que ya existe.
 * 2. Solicitudes que cambian datos SOLO desde la misma pagina de Nexorix:
 *    si el navegador dice que vienen de otro sitio (Origin o Sec-Fetch-Site),
 *    se rechazan. Protege contra CSRF junto con la cookie SameSite.
 * 3. Cabeceras extra: Permissions-Policy (sin camara, microfono ni ubicacion).
 */
public class AbuseProtectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AbuseProtectionFilter.class);

    private record Rule(String method, String path, RateLimiter limiter, String message) {
    }

    private final List<Rule> rules = List.of(
            new Rule("POST", "/api/auth/login", new RateLimiter(20, Duration.ofMinutes(5)),
                    "Demasiados intentos de inicio de sesión desde tu red. Espera unos minutos."),
            new Rule("POST", "/api/auth/pin", new RateLimiter(30, Duration.ofMinutes(5)),
                    "Demasiados intentos. Espera unos minutos."),
            new Rule("POST", "/api/users", new RateLimiter(10, Duration.ofMinutes(30)),
                    "Se crearon muchas cuentas desde tu red. Intenta más tarde."),
            new Rule("POST", "/api/recovery/start", new RateLimiter(10, Duration.ofMinutes(30)),
                    "Demasiadas solicitudes de recuperación. Intenta más tarde."),
            new Rule("POST", "/api/recovery/code", new RateLimiter(20, Duration.ofMinutes(15)),
                    "Demasiados intentos. Espera unos minutos.")
    );

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** Rutas que reciben llamadas de otros servidores (no de navegadores). */
    private static final Set<String> SERVER_TO_SERVER = Set.of("/api/kyc/webhook", "/api/whatsapp/webhook", "/api/bank/webhook");

    private final String publicUrl;
    private final boolean ipLimits;

    /**
     * @param ipLimits false solo para pruebas de carga (muchas cuentas desde un mismo computador).
     */
    public AbuseProtectionFilter(String publicUrl, boolean ipLimits) {
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        this.ipLimits = ipLimits;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        if (request.getRequestURI().startsWith("/api/")) {
            // Datos financieros: ni el navegador ni un proxy deben guardarlos.
            response.setHeader("Cache-Control", "no-store, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }

        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();

        if (!SAFE_METHODS.contains(method) && path.startsWith("/api/") && !SERVER_TO_SERVER.contains(path)
                && !sameOrigin(request)) {
            log.warn("Solicitud de otro sitio bloqueada: {} {} (Origin: {})", method, path, request.getHeader("Origin"));
            reject(response, 403, "Solicitud bloqueada: debe hacerse desde la página de Nexorix.");
            return;
        }

        for (Rule rule : ipLimits ? rules : List.<Rule>of()) {
            if (rule.method().equals(method) && rule.path().equals(path)
                    && !rule.limiter().tryAcquire(request.getRemoteAddr())) {
                long wait = rule.limiter().secondsUntilAvailable(request.getRemoteAddr());
                response.setHeader("Retry-After", String.valueOf(wait));
                reject(response, 429, rule.message());
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * El navegador manda Origin y Sec-Fetch-Site en las solicitudes que cambian datos.
     * Herramientas sin navegador (Postman, k6) no los mandan: se permiten,
     * porque no pueden usar la cookie de sesion de otra persona.
     */
    boolean sameOrigin(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite)) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (!publicUrl.isEmpty() && origin.equalsIgnoreCase(publicUrl)) {
            return true;
        }
        try {
            String originHost = URI.create(origin).getAuthority();
            String host = request.getHeader("Host");
            return originHost != null && host != null && originHost.equalsIgnoreCase(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
