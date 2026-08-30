package com.ecommerce.notification.service.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilter_sinCabecera_generaUuidYLoEcheaEnLaRespuesta() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        String cid = response.getHeader("X-Correlation-Id");
        assertNotNull(cid);
        assertTrue(cid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void doFilter_cabeceraValida_laReusaTalCual() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Correlation-Id", "test-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // ACT
        filter.doFilter(request, response, chain);

        // ASSERT
        assertEquals("test-abc-123", response.getHeader("X-Correlation-Id"));
    }

    @Test
    void doFilter_cabeceraConInyeccionDeLog_generaUuidNuevoYNoLaEchea() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Correlation-Id", "abc\r\nFAKE: injected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
        request.addHeader("X-Correlation-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notificaciones");
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
}
