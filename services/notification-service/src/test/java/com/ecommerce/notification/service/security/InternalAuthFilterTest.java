package com.ecommerce.notification.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class InternalAuthFilterTest {

    private static final String SECRET = "shared-internal-secret";

    private InternalAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
    }

    @Test
    void doFilter_sinCabecerasInternas_retorna401YNoInvocaLaCadena() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Autenticación interna requerida"));
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_timestampExpirado_retorna401CredencialExpirada() throws Exception {

        // ARRANGE
        long timestampViejo = System.currentTimeMillis() - 31_000;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestampViejo));
        request.addHeader("X-Internal-Signature", sign("ms-pedidos", timestampViejo));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Credencial interna expirada"));
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_firmaForjada_retorna401FirmaInvalida() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", "9".repeat(64));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Firma interna inválida"));
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_apiGatewayLlamando_retorna403() throws Exception {

        // ARRANGE — solo ms-pedidos está autorizado, ni siquiera api-gateway
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("api-gateway", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Servicio no autorizado para este recurso"));
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_msPedidosLlamando_continuaLaCadena() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-pedidos", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void validarConfiguracion_secretoVacio_lanzaIllegalStateException() {

        // ARRANGE
        ReflectionTestUtils.setField(filter, "secret", "");

        // ACT & ASSERT
        assertThrows(IllegalStateException.class, () -> filter.validarConfiguracion());
    }

    @Test
    void validarConfiguracion_secretoNulo_lanzaIllegalStateException() {

        // ARRANGE
        ReflectionTestUtils.setField(filter, "secret", null);

        // ACT & ASSERT
        assertThrows(IllegalStateException.class, () -> filter.validarConfiguracion());
    }

    private static String sign(String service, long timestamp) throws Exception {
        String stringToSign = service + ":" + timestamp;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    }
}
