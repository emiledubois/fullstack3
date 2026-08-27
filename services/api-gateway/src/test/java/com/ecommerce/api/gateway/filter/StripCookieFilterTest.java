package com.ecommerce.api.gateway.filter;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripCookieFilterTest {

    private final StripCookieFilter stripCookieFilter = new StripCookieFilter();

    @Test
    void filter_eliminaElHeaderCookieAntesDeReenviarAlMicroservicio() throws Exception {

        // ARRANGE
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        servletRequest.setCookies(new Cookie("sl_jwt", "jwt-secreto"));
        servletRequest.addHeader("X-User-Email", "user@pyme.cl");
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        @SuppressWarnings("unchecked")
        HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        // ACT
        stripCookieFilter.filter(req, next);

        // ASSERT
        ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
        verify(next).handle(captor.capture());
        assertNull(captor.getValue().headers().firstHeader(HttpHeaders.COOKIE));
        // El resto de los headers (como X-User-Email) no deben verse afectados
        org.junit.jupiter.api.Assertions.assertEquals("user@pyme.cl",
                captor.getValue().headers().firstHeader("X-User-Email"));
    }
}
