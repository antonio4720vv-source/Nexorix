package com.nexorix.security;

import com.nexorix.config.AbuseProtectionFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpDefenseFilterTest {

    private SecurityEventRepository events;
    private BannedIpRepository bans;
    private AbuseIpDbReporter reporter;
    private IpDefense defense;
    private AbuseProtectionFilter filter;

    @BeforeEach
    void setUp() {
        events = mock(SecurityEventRepository.class);
        bans = mock(BannedIpRepository.class);
        reporter = mock(AbuseIpDbReporter.class);
        when(reporter.enabled()).thenReturn(true);
        defense = new IpDefense(events, bans, reporter, 24, 3, 5, 0, 5, "203.0.113.99");
        filter = new AbuseProtectionFilter("http://localhost:8080", true, defense);
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        request.addHeader("Host", "localhost:8080");
        request.addHeader("User-Agent", "sqlmap/1.7");
        request.addHeader("Cookie", "JSESSIONID=secreto");
        return request;
    }

    private static MockHttpServletRequest json(String path, String ip, String body) {
        MockHttpServletRequest request = request("POST", path, ip);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void rutaTrampaBaneaALaIpYLaSiguienteSolicitudSeRechaza() throws Exception {
        assertThat(send(request("GET", "/wp-login.php", "198.51.100.7")).getStatus()).isEqualTo(403);
        assertThat(defense.isBanned("198.51.100.7")).isTrue();

        MockHttpServletResponse next = send(request("GET", "/index.html", "198.51.100.7"));
        assertThat(next.getStatus()).isEqualTo(403);
        // Otra IP no se ve afectada.
        assertThat(send(request("GET", "/index.html", "198.51.100.8")).getStatus()).isEqualTo(200);
    }

    @Test
    void rutaTrampaDeLaApiResponde401SinPistas() throws Exception {
        MockHttpServletResponse response = send(request("GET", "/api/admin/users", "198.51.100.9"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContainIgnoringCase("honeypot", "trampa");
    }

    @Test
    void campoOcultoLlenoBanea() throws Exception {
        MockHttpServletResponse response = send(json("/api/users", "198.51.100.10",
                "{\"name\":\"Bot\",\"nx_website\":\"http://spam.example\"}"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(defense.isBanned("198.51.100.10")).isTrue();
    }

    @Test
    void campoOcultoVacioDejaPasarYElControladorPuedeLeerElCuerpo() throws Exception {
        MockHttpServletRequest request = json("/api/auth/login", "198.51.100.11",
                "{\"username\":\"demo\",\"password\":\"Demo1234!\",\"nx_website\":\"\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];
        FilterChain chain = (req, res) -> seen[0] = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen[0]).contains("\"username\":\"demo\"");
        assertThat(defense.isBanned("198.51.100.11")).isFalse();
    }

    @Test
    void contrasenaConSimbolosNoSeTomaPorAtaque() throws Exception {
        MockHttpServletResponse response = send(json("/api/auth/login", "198.51.100.12",
                "{\"username\":\"demo\",\"password\":\"' or '1'='1 <script> ../\"}"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void payloadDeInyeccionSeRechazaYALaTerceraVezBanea() throws Exception {
        String body = "{\"username\":\"admin' OR 1=1 --\",\"password\":\"x\"}";
        assertThat(send(json("/api/auth/login", "198.51.100.13", body)).getStatus()).isEqualTo(400);
        assertThat(send(json("/api/auth/login", "198.51.100.13", body)).getStatus()).isEqualTo(400);
        assertThat(defense.isBanned("198.51.100.13")).isFalse();
        send(json("/api/auth/login", "198.51.100.13", body));
        assertThat(defense.isBanned("198.51.100.13")).isTrue();
    }

    @Test
    void consultaConInyeccionSeRechaza() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/transactions", "198.51.100.14");
        request.setQueryString("q=1%20UNION%20SELECT%20password%20FROM%20users");
        assertThat(send(request).getStatus()).isEqualTo(400);
    }

    @Test
    void muchosFallosDeInicioDeSesionBanean() throws Exception {
        AbuseProtectionFilter failing = new AbuseProtectionFilter("http://localhost:8080", true, defense);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401);
            failing.doFilter(json("/api/auth/login", "198.51.100.15", "{\"username\":\"a\",\"password\":\"b\"}"),
                    response, chain);
        }
        assertThat(defense.isBanned("198.51.100.15")).isTrue();
    }

    @Test
    void inundacionDeSolicitudesSeFrenaYBanea() throws Exception {
        int rejected = 0;
        for (int i = 0; i < 80; i++) {
            if (send(request("GET", "/nexorix.css", "198.51.100.16")).getStatus() != 200) {
                rejected++;
            }
        }
        assertThat(rejected).isGreaterThan(0);
        assertThat(defense.isBanned("198.51.100.16")).isTrue();
    }

    @Test
    void ipExentaNuncaSeBanea() throws Exception {
        send(request("GET", "/.env", "203.0.113.99"));
        send(request("GET", "/.env", "127.0.0.1"));
        assertThat(defense.isBanned("203.0.113.99")).isFalse();
        assertThat(defense.isBanned("127.0.0.1")).isFalse();
    }

    @Test
    void elBaneoSeGuardaSeReportaYElRegistroNoLlevaCookies() throws Exception {
        send(request("GET", "/phpmyadmin", "198.51.100.17"));

        verify(bans, timeout(2000)).save(org.mockito.ArgumentMatchers.any(BannedIp.class));
        verify(reporter, timeout(2000)).report(org.mockito.ArgumentMatchers.eq("198.51.100.17"),
                org.mockito.ArgumentMatchers.contains("21"), anyString());
        org.mockito.ArgumentCaptor<SecurityEvent> captor = org.mockito.ArgumentCaptor.forClass(SecurityEvent.class);
        verify(events, timeout(2000).atLeastOnce()).save(captor.capture());
        SecurityEvent event = captor.getAllValues().get(0);
        assertThat(event.getIp()).isEqualTo("198.51.100.17");
        assertThat(event.getType()).isEqualTo("HONEYPOT_RUTA");
        assertThat(event.getUserAgent()).isEqualTo("sqlmap/1.7");
        assertThat(event.getHeaders()).contains("User-Agent").doesNotContain("JSESSIONID");
    }

    @Test
    void ipPrivadaNoSeReportaAbuseIpDb() {
        assertThat(IpDefense.isPrivate("192.168.1.5")).isTrue();
        assertThat(IpDefense.isPrivate("127.0.0.1")).isTrue();
        assertThat(IpDefense.isPrivate("fd00::1")).isTrue();
        assertThat(IpDefense.isPrivate("198.51.100.7")).isFalse();
    }

    @Test
    void sinDefensaElFiltroNoCambiaDeComportamiento() throws Exception {
        AbuseProtectionFilter plain = new AbuseProtectionFilter("http://localhost:8080", true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        plain.doFilter(request("GET", "/wp-login.php", "198.51.100.18"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
        verify(bans, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
