package com.smartlogix.envios.security;

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
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/envios/1/cancelar");
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
    void doFilter_firmaForjada_retorna401FirmaInvalida() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/envios/1/cancelar");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", "0".repeat(64));
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
    void doFilter_apiGatewayLlamandoCancelar_retorna403() throws Exception {

        // ARRANGE — cancelar (compensación de la Saga) solo acepta ms-pedidos
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/envios/1/cancelar");
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
    void doFilter_msPedidosLlamandoCancelar_continuaLaCadena() throws Exception {

        // ARRANGE
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/envios/1/cancelar");
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
    void doFilter_msPedidosCreandoEnvio_continuaLaCadena() throws Exception {

        // ARRANGE — POST /envios acepta tanto api-gateway (creación de
        // usuario) como ms-pedidos (paso de la Saga)
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/envios");
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
    void doFilter_msPedidosListandoEnvios_retorna403() throws Exception {

        // ARRANGE — ms-pedidos no está autorizado para el resto de /envios/**
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/envios");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-pedidos", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_sinCabecerasInternasSobreEndpointInternoPorPedidos_retorna401() throws Exception {

        // ARRANGE — regresión IDOR: sin credencial interna, nunca debería
        // llegar a leer envíos ajenos (criterio de aceptación 5e del diseño ARCO+)
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/envios/interno/por-pedidos");
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
    void doFilter_firmaValidaDeApiGatewaySobreEndpointInternoPorPedidos_continuaLaCadena() throws Exception {

        // ARRANGE — cae en el bucket por defecto ("todo lo demás → api-gateway")
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/envios/interno/por-pedidos");
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
    void doFilter_firmaValidaDeMsPedidosSobreEndpointInternoPorPedidos_retorna403() throws Exception {

        // ARRANGE — ms-pedidos solo está autorizado en /envios/{id}/cancelar
        // y en POST /envios, no en el nuevo endpoint interno de lectura
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/envios/interno/por-pedidos");
        request.addHeader("X-Internal-Service", "ms-pedidos");
        request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Internal-Signature", sign("ms-pedidos", timestamp));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
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
