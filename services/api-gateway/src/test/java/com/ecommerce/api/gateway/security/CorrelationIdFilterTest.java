package com.ecommerce.api.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilter_sinCabecera_generaUuidYLoEcheaEnLaRespuesta() throws Exception {

        // ARRANGE — criterio de aceptación 1 (docs/designs/observability-correlation-ids.md §11)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/usuarios/me/cancelacion");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        String cid = response.getHeader("X-Correlation-Id");
        assertNotNull(cid);
        assertTrue(cid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void doFilter_cabeceraValida_laReusaTalCual() throws Exception {

        // ARRANGE — criterio de aceptación 2
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/usuarios/me/cancelacion");
        request.addHeader("X-Correlation-Id", "test-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals("test-abc-123", response.getHeader("X-Correlation-Id"));
    }

    @Test
    void doFilter_cabeceraConInyeccionDeLog_generaUuidNuevoYNoLaEchea() throws Exception {

        // ARRANGE — criterio de aceptación 6
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.addHeader("X-Correlation-Id", "abc\r\nFAKE: injected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        String cid = response.getHeader("X-Correlation-Id");
        assertNotNull(cid);
        assertFalse(cid.contains("FAKE"));
        assertFalse(cid.contains("\r"));
        assertEquals(36, cid.length());
    }

    @Test
    void doFilter_cabeceraSobredimensionada_generaUuidNuevo() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.addHeader("X-Correlation-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        String cid = response.getHeader("X-Correlation-Id");
        assertNotEquals("a".repeat(65), cid);
        assertEquals(36, cid.length());
    }

    @Test
    void doFilter_fijaMdcDuranteLaCadenaYLoLimpiaAlFinalizar() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.addHeader("X-Correlation-Id", "test-mdc-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] mdcDuranteCadena = new String[1];
        FilterChain chain = (req, res) -> mdcDuranteCadena[0] = MDC.get("correlationId");

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals("test-mdc-1", mdcDuranteCadena[0]);
        assertNull(MDC.get("correlationId"));
    }

    // Los siguientes dos tests ejercitan el HttpServletRequestWrapper del
    // §5.2 paso 3: lo que RouterFunction/HandlerFunctions.http reenvía al
    // destino proxeado se construye a partir de este mismo HttpServletRequest
    // (getHeader/getHeaders/getHeaderNames), así que deben ver el valor
    // canónico (validado o generado), no el original crudo del cliente.

    @Test
    void doFilter_cabeceraMalformada_elRequestEnvueltoExponeElValorCanonicoNoElOriginal() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        request.addHeader("X-Correlation-Id", "abc\r\nFAKE: injected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final HttpServletRequest[] visto = new HttpServletRequest[1];
        FilterChain chain = (req, res) -> visto[0] = (HttpServletRequest) req;

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        String reenviado = visto[0].getHeader("X-Correlation-Id");
        assertNotNull(reenviado);
        assertFalse(reenviado.contains("FAKE"));
        assertEquals(36, reenviado.length());
        assertEquals(List.of(reenviado), Collections.list(visto[0].getHeaders("X-Correlation-Id")));
        assertTrue(Collections.list(visto[0].getHeaderNames()).stream()
                .anyMatch("X-Correlation-Id"::equalsIgnoreCase));
    }

    @Test
    void doFilter_sinCabeceraEntrante_elRequestEnvueltoIncluyeLaCabeceraGenerada() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final HttpServletRequest[] visto = new HttpServletRequest[1];
        FilterChain chain = (req, res) -> visto[0] = (HttpServletRequest) req;

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertNotNull(visto[0].getHeader("X-Correlation-Id"));
        assertTrue(Collections.list(visto[0].getHeaderNames()).stream()
                .anyMatch("X-Correlation-Id"::equalsIgnoreCase));
    }
}
