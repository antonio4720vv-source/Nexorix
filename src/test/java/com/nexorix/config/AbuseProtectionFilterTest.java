package com.nexorix.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseProtectionFilterTest {

    private final AbuseProtectionFilter filter = new AbuseProtectionFilter("https://nexorix.ngrok-free.dev", true);

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Host", "localhost:8080");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void permiteSolicitudesDeLaMismaPagina() throws Exception {
        MockHttpServletRequest request = post("/api/trace/groups");
        request.addHeader("Origin", "http://localhost:8080");
        request.addHeader("Sec-Fetch-Site", "same-origin");

        assertThat(run(request).getStatus()).isEqualTo(200);
    }

    @Test
    void permiteLaDireccionPublicaConfigurada() throws Exception {
        MockHttpServletRequest request = post("/api/trace/groups");
        request.addHeader("Origin", "https://nexorix.ngrok-free.dev");

        assertThat(run(request).getStatus()).isEqualTo(200);
    }

    @Test
    void bloqueaSolicitudesQueCambianDatosDesdeOtroSitio() throws Exception {
        MockHttpServletRequest request = post("/api/transactions");
        request.addHeader("Origin", "https://sitio-malicioso.com");

        MockHttpServletResponse response = run(request);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("error");

        MockHttpServletRequest crossSite = post("/api/transactions");
        crossSite.addHeader("Sec-Fetch-Site", "cross-site");
        assertThat(run(crossSite).getStatus()).isEqualTo(403);
    }

    @Test
    void elWebhookDeDiditNoSeBloquea() throws Exception {
        MockHttpServletRequest request = post("/api/kyc/webhook");
        request.addHeader("Origin", "https://didit.me");

        assertThat(run(request).getStatus()).isEqualTo(200);
    }

    @Test
    void limitaLosIntentosDeInicioDeSesionPorIp() throws Exception {
        int last = 0;
        for (int i = 0; i < 21; i++) {
            last = run(post("/api/auth/login")).getStatus();
        }
        assertThat(last).isEqualTo(429);

        MockHttpServletRequest otherIp = post("/api/auth/login");
        otherIp.setRemoteAddr("10.0.0.2");
        assertThat(run(otherIp).getStatus()).isEqualTo(200);
    }

    @Test
    void sinLimitesParaPruebasDeCarga() throws Exception {
        AbuseProtectionFilter loadTest = new AbuseProtectionFilter("", false);
        int last = 0;
        for (int i = 0; i < 30; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            loadTest.doFilter(post("/api/auth/login"), response, new MockFilterChain());
            last = response.getStatus();
        }
        assertThat(last).isEqualTo(200);
    }

    @Test
    void agregaPermissionsPolicy() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/dashboard.html")).getHeader("Permissions-Policy"))
                .contains("camera=()");
    }
}
