package com.nexorix.security;

import com.nexorix.ai.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Defensa por IP: baneo dinamico de 24 horas, tarpit (respuestas lentas para atacantes),
 * registro de lo que hicieron y reporte a AbuseIPDB.
 *
 * - Una IP baneada recibe 403 despues de esperar (tarpit), sin ninguna pista.
 * - El baneo se guarda en la base de datos: sobrevive a un reinicio.
 * - Guardar el registro y reportar se hace en otro hilo: nunca frena ni tumba una solicitud.
 * - Loopback y la lista nexorix.security.ban-allowlist nunca se banean.
 */
@Service
public class IpDefense {

    private static final Logger log = LoggerFactory.getLogger(IpDefense.class);

    /** Categorias de AbuseIPDB. */
    public static final String CAT_BRUTE_FORCE = "18";
    public static final String CAT_WEB_ATTACK = "21";
    public static final String CAT_BAD_BOT = "19";

    private static final int EVENT_RETENTION_DAYS = 90;
    private static final Set<String> SECRET_HEADERS = Set.of(
            "cookie", "authorization", "proxy-authorization", "x-api-key", "x-csrf-token");

    private final SecurityEventRepository events;
    private final BannedIpRepository bans;
    private final AbuseIpDbReporter reporter;

    private final Duration banDuration;
    private final Set<String> allowlist;
    private final long tarpitMillis;
    private final Semaphore tarpitSlots;

    private final Map<String, LocalDateTime> banned = new ConcurrentHashMap<>();
    private final RateLimiter requestsPerSecond;
    private final RateLimiter floodStrikes = new RateLimiter(20, Duration.ofMinutes(10));
    private final RateLimiter failedLogins;
    private final RateLimiter attackStrikes = new RateLimiter(2, Duration.ofMinutes(10));
    private final RateLimiter eventsPerIp = new RateLimiter(30, Duration.ofHours(1));

    private final ThreadPoolExecutor background = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500), runnable -> {
        Thread thread = new Thread(runnable, "ip-defense");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());

    public IpDefense(
            SecurityEventRepository events,
            BannedIpRepository bans,
            AbuseIpDbReporter reporter,
            @Value("${nexorix.security.ban-hours:24}") long banHours,
            @Value("${nexorix.security.max-failed-logins:10}") int maxFailedLogins,
            @Value("${nexorix.security.max-requests-per-second:50}") int maxRequestsPerSecond,
            @Value("${nexorix.security.tarpit-seconds:30}") long tarpitSeconds,
            @Value("${nexorix.security.tarpit-max-concurrent:25}") int tarpitMaxConcurrent,
            @Value("${nexorix.security.ban-allowlist:}") String allowlist
    ) {
        this.events = events;
        this.bans = bans;
        this.reporter = reporter;
        this.banDuration = Duration.ofHours(Math.max(1, banHours));
        // Se banea al llegar al N-esimo fallo: el limitador deja pasar N-1.
        this.failedLogins = new RateLimiter(Math.max(1, maxFailedLogins - 1), Duration.ofMinutes(15));
        this.requestsPerSecond = new RateLimiter(Math.max(1, maxRequestsPerSecond), Duration.ofSeconds(1));
        this.tarpitMillis = Math.max(0, tarpitSeconds) * 1000;
        this.tarpitSlots = new Semaphore(Math.max(1, tarpitMaxConcurrent));
        this.allowlist = allowlist == null ? Set.of()
                : Arrays.stream(allowlist.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void restore() {
        try {
            LocalDateTime now = LocalDateTime.now();
            bans.deleteExpired(now);
            events.deleteOlderThan(now.minusDays(EVENT_RETENTION_DAYS));
            bans.findByBannedUntilAfter(now).forEach(ban -> banned.put(ban.getIp(), ban.getBannedUntil()));
            log.info("Defensa por IP lista: {} IP(s) siguen baneadas", banned.size());
        } catch (RuntimeException exception) {
            log.error("No se pudieron cargar los baneos guardados", exception);
        }
    }

    @PreDestroy
    void stop() {
        background.shutdown();
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    public boolean isBanned(String ip) {
        LocalDateTime until = banned.get(ip);
        if (until == null) {
            return false;
        }
        if (until.isBefore(LocalDateTime.now())) {
            banned.remove(ip, until);
            return false;
        }
        return true;
    }

    public boolean isExempt(String ip) {
        if (ip == null || allowlist.contains(ip)) {
            return true;
        }
        try {
            return !ip.isBlank() && looksLikeLiteral(ip) && InetAddress.getByName(ip).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    // ============================================================
    // CONTADORES QUE LLEVAN AL BANEO
    // ============================================================

    /** true si la IP supero el limite de solicitudes por segundo (y, si insiste, la banea). */
    public boolean tooManyRequests(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (isExempt(ip) || requestsPerSecond.tryAcquire(ip)) {
            return false;
        }
        if (!floodStrikes.tryAcquire(ip)) {
            audit(request, "INUNDACION", null, "Limite de solicitudes por segundo superado de forma repetida");
            ban(ip, "Inundacion de solicitudes", null);
        }
        return true;
    }

    /** Un inicio de sesion / PIN / codigo fallido. Muchos seguidos = baneo de 24 horas. */
    public void failedLogin(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (isExempt(ip) || failedLogins.tryAcquire(ip)) {
            return;
        }
        audit(request, "FUERZA_BRUTA", null, "Fallos de autenticacion repetidos");
        ban(ip, "Fuerza bruta en el inicio de sesion", CAT_BRUTE_FORCE);
    }

    /** Payload sospechoso: se registra siempre; a la tercera vez en 10 minutos, baneo. */
    public void suspiciousPayload(HttpServletRequest request, String payload, String detail) {
        String ip = request.getRemoteAddr();
        audit(request, "PAYLOAD_SOSPECHOSO", payload, detail);
        if (!isExempt(ip) && !attackStrikes.tryAcquire(ip)) {
            ban(ip, "Payloads de ataque repetidos", CAT_WEB_ATTACK);
        }
    }

    /** Cayo en una trampa: baneo inmediato (ninguna persona real llega ahi). */
    public void trapped(HttpServletRequest request, String type, String payload, String detail, String categories) {
        String ip = request.getRemoteAddr();
        audit(request, type, payload, detail);
        ban(ip, detail, categories);
    }

    // ============================================================
    // BANEO, TARPIT, REGISTRO
    // ============================================================

    /** @param abuseCategories categorias de AbuseIPDB; null = no reportar */
    public void ban(String ip, String reason, String abuseCategories) {
        if (isExempt(ip) || isBanned(ip)) {
            return;
        }
        LocalDateTime until = LocalDateTime.now().plus(banDuration);
        banned.put(ip, until);
        log.warn("IP baneada {} horas: {} ({})", banDuration.toHours(), ip, reason);
        background(() -> {
            BannedIp entity = new BannedIp(ip, truncate(reason, 200), until);
            bans.save(entity);
            if (abuseCategories != null && reporter.enabled()
                    && reporter.report(ip, abuseCategories, "Nexorix: " + reason)) {
                entity.setReported(true);
                bans.save(entity);
            }
        });
        audit(ip, "BAN", null, null, null, null, null, truncate(reason, 300));
    }

    /**
     * Hace esperar al atacante (consume su tiempo y sus conexiones, no le da informacion).
     * Cada espera ocupa un hilo del servidor, por eso hay un tope de esperas a la vez:
     * si se llena, se responde de inmediato en vez de dejar que nos agoten los hilos.
     */
    public void tarpit() {
        if (tarpitMillis <= 0 || !tarpitSlots.tryAcquire()) {
            return;
        }
        try {
            Thread.sleep(tarpitMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            tarpitSlots.release();
        }
    }

    public void audit(HttpServletRequest request, String type, String payload, String detail) {
        audit(request.getRemoteAddr(), type, request.getMethod(),
                truncate(request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString()), 300),
                request.getHeader("User-Agent"), safeHeaders(request), payload, detail);
    }

    private void audit(String ip, String type, String method, String path, String userAgent,
                       String headers, String payload, String detail) {
        // Tope por IP para que un atacante no llene la base de datos con su propio ruido.
        if (!"BAN".equals(type) && !eventsPerIp.tryAcquire(ip)) {
            return;
        }
        SecurityEvent event = new SecurityEvent(ip, type, method, path, truncate(userAgent, 300),
                truncate(headers, 2000), truncate(payload, 600), truncate(detail, 300));
        background(() -> events.save(event));
    }

    private void background(Runnable task) {
        background.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                log.error("Fallo guardando datos de seguridad", exception);
            }
        });
    }

    /** Cabeceras sin cookies ni credenciales. */
    static String safeHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return "";
        }
        return Collections.list(names).stream()
                .filter(name -> !SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> name + ": " + truncate(request.getHeader(name), 200))
                .collect(Collectors.joining("\n"));
    }

    // ============================================================
    // UTILIDADES
    // ============================================================

    /** IP de red local o loopback: no se reporta a AbuseIPDB. */
    static boolean isPrivate(String ip) {
        try {
            if (ip == null || !looksLikeLiteral(ip)) {
                return true;
            }
            InetAddress address = InetAddress.getByName(ip);
            byte first = address.getAddress()[0];
            boolean uniqueLocalV6 = address.getAddress().length == 16 && (first & 0xfe) == 0xfc;
            return address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress() || uniqueLocalV6;
        } catch (Exception exception) {
            return true;
        }
    }

    private static boolean looksLikeLiteral(String ip) {
        return ip.indexOf(':') >= 0 || ip.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
