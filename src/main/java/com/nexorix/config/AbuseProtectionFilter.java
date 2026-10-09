package com.nexorix.config;

import com.nexorix.ai.RateLimiter;
import com.nexorix.security.IpDefense;
import com.nexorix.security.PayloadInspector;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Proteccion operativa (antes de llegar a los controladores):
 *
 * 0. Defensa activa (si hay IpDefense): IP baneada = respuesta lenta (tarpit) y 403; rutas y
 *    campos trampa (honeypots) = baneo inmediato; demasiadas solicitudes por segundo, demasiados
 *    inicios de sesion fallidos o payloads de ataque = baneo de 24 horas. Todo queda registrado.
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

    /**
     * Rutas trampa: no existen en Nexorix y ninguna persona las abre. Solo las visitan escaneres y bots
     * (algunas aparecen escondidas en el HTML y en robots.txt). Quien las toque queda baneado.
     */
    private static final Pattern TRAPS = Pattern.compile(
            "(?i)^/(admin(istrator)?(-panel)?|wp-(login\\.php|admin|content|includes)|xmlrpc\\.php|phpmyadmin|pma|"
                    + "\\.env.*|\\.git(/.*)?|\\.aws/.*|actuator(/.*)?|server-status|cgi-bin(/.*)?|config\\.php|"
                    + "backup\\.(sql|zip|tar\\.gz)|vendor/phpunit/.*|api/admin(/.*)?|api/internal(/.*)?|api/debug(/.*)?)/?$");

    /** Rutas publicas donde se revisa el cuerpo (campo trampa y payloads). */
    private static final Set<String> INSPECTED_BODIES = Set.of("/api/auth/login", "/api/users", "/api/recovery/start");

    /** Si una de estas responde mal, cuenta como intento fallido (para el baneo por fuerza bruta). */
    private static final Map<String, Set<Integer>> FAILURE_STATUS = Map.of(
            "/api/auth/login", Set.of(401, 423),
            "/api/auth/pin", Set.of(401, 423),
            "/api/recovery/code", Set.of(400, 401, 423)
    );

    private static final int MAX_INSPECTED_BODY = 16 * 1024;

    private final String publicUrl;
    private final boolean ipLimits;
    private final IpDefense defense;

    /** Sin defensa activa (pruebas unitarias del resto de reglas). */
    public AbuseProtectionFilter(String publicUrl, boolean ipLimits) {
        this(publicUrl, ipLimits, null);
    }

    /**
     * @param ipLimits false solo para pruebas de carga (muchas cuentas desde un mismo computador):
     *                 apaga los limites por IP, pero las trampas siguen activas.
     * @param defense  baneos, tarpit y registro; null = apagado
     */
    public AbuseProtectionFilter(String publicUrl, boolean ipLimits, IpDefense defense) {
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        this.ipLimits = ipLimits;
        this.defense = defense;
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

        if (defense != null) {
            HttpServletRequest checked = defend(request, response, method, path);
            if (checked == null) {
                return;
            }
            request = checked;
        }

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

        if (defense != null && ipLimits) {
            Set<Integer> failures = FAILURE_STATUS.get(path);
            if ("POST".equals(method) && failures != null && failures.contains(response.getStatus())) {
                defense.failedLogin(request);
            }
        }
    }

    /**
     * Defensa activa. Devuelve la solicitud a seguir procesando (con el cuerpo releible)
     * o null si ya se respondio y hay que parar.
     */
    private HttpServletRequest defend(HttpServletRequest request, HttpServletResponse response,
                                      String method, String path) throws IOException {
        String ip = request.getRemoteAddr();

        if (defense.isBanned(ip)) {
            defense.tarpit();
            reject(response, 403, "Acceso denegado.");
            return null;
        }

        if (TRAPS.matcher(path).matches()) {
            defense.trapped(request, "HONEYPOT_RUTA", null, "Visito la ruta trampa " + path,
                    IpDefense.CAT_WEB_ATTACK + "," + IpDefense.CAT_BAD_BOT);
            return trapResponse(response, path);
        }

        if (ipLimits && defense.tooManyRequests(request)) {
            response.setHeader("Retry-After", "5");
            reject(response, 429, "Demasiadas solicitudes. Espera un momento.");
            return null;
        }

        String urlHit = PayloadInspector.inspectUrl(path, request.getQueryString());
        if (urlHit != null) {
            defense.suspiciousPayload(request, truncate(request.getQueryString()), urlHit);
            reject(response, 400, "Solicitud no válida.");
            return null;
        }

        if ("POST".equals(method) && INSPECTED_BODIES.contains(path)) {
            return inspectBody(request, response);
        }
        return request;
    }

    private HttpServletRequest inspectBody(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (request.getContentLengthLong() > MAX_INSPECTED_BODY) {
            reject(response, 413, "Solicitud demasiado grande.");
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_INSPECTED_BODY + 1);
        if (body.length > MAX_INSPECTED_BODY) {
            reject(response, 413, "Solicitud demasiado grande.");
            return null;
        }

        PayloadInspector.Result result = PayloadInspector.inspectBody(body);
        if (result.honeypotFilled()) {
            defense.trapped(request, "HONEYPOT_CAMPO", result.safeBody(),
                    "Lleno el campo oculto " + PayloadInspector.HONEYPOT_FIELD, IpDefense.CAT_BAD_BOT);
            defense.tarpit();
            // Respuesta que no delata la trampa: parece un inicio de sesion fallido.
            reject(response, 401, "Datos incorrectos.");
            return null;
        }
        if (result.suspicious() != null) {
            defense.suspiciousPayload(request, result.safeBody(), result.suspicious());
            reject(response, 400, "Solicitud no válida.");
            return null;
        }
        return com.nexorix.security.PayloadInspector.cached(request, body);
    }

    /** Respuesta a quien toca una ruta trampa: lenta y sin pistas (parece una zona protegida). */
    private HttpServletRequest trapResponse(HttpServletResponse response, String path) throws IOException {
        defense.tarpit();
        if (path.startsWith("/api/")) {
            reject(response, 401, "No autorizado.");
        } else {
            reject(response, 403, "Acceso denegado.");
        }
        return null;
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 500));
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
