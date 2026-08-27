package com.smartlogix.pedidos.security;

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
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
    void doFilter_timestampNoParseable_retorna401CredencialInvalida() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "ms-pagos");
        request.addHeader("X-Internal-Timestamp", "no-es-un-numero");
        request.addHeader("X-Internal-Signature", "cualquiera");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Autenticación interna inválida"));
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_timestampExpirado_retorna401CredencialExpirada() throws Exception {

        // ARRANGE — firma válida, pero con más de 30s de antigüedad
        long timestampViejo = System.currentTimeMillis() - 60_000;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "ms-pagos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestampViejo));
        request.addHeader("X-Internal-Signature", sign("ms-pagos", timestampViejo));
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

        // ARRANGE — timestamp fresco pero firma que no corresponde al secreto
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "ms-pagos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", "a".repeat(64));
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
    void doFilter_firmaValidaDeMsInventarioSobreConfirmarPago_retorna403() throws Exception {

        // ARRANGE — regresión literal del gap de cumplimiento: firma
        // técnicamente válida pero de un emisor no autorizado para este path
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "ms-inventario");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-inventario", timestamp));
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
    void doFilter_firmaValidaDeApiGatewaySobreConfirmarPago_retorna403() throws Exception {

        // ARRANGE — api-gateway es un emisor confiable en general, pero
        // NO está en el allowlist de confirmar-pago (solo ms-pagos)
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("api-gateway", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_firmaValidaDeMsPagosSobreConfirmarPago_continuaLaCadena() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos/1/confirmar-pago");
        request.addHeader("X-Internal-Service", "ms-pagos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-pagos", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void doFilter_firmaValidaDeMsPagosSobreCrearPedido_retorna403() throws Exception {

        // ARRANGE — ms-pagos solo está autorizado para confirmar-pago/pago-fallido,
        // no para el resto de /pedidos/**
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos");
        request.addHeader("X-Internal-Service", "ms-pagos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-pagos", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_firmaValidaDeApiGatewaySobreCrearPedido_continuaLaCadena() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pedidos");
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("api-gateway", timestamp));
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
