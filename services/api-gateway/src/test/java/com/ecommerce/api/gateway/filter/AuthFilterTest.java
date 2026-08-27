package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthFilter authFilter;

    @Mock private HandlerFunction<ServerResponse> next;

    @Test
    void filter_sinCookie_retorna401TokenRequerido() throws Exception {

        // ARRANGE
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        // ACT
        ServerResponse response = authFilter.filter(req, next);

        // ASSERT
        assertEquals(401, response.statusCode().value());
        verify(next, never()).handle(any());
    }

    @Test
    void filter_cookieConFirmaInvalida_retorna401TokenInvalido() throws Exception {

        // ARRANGE
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        servletRequest.setCookies(new Cookie("sl_jwt", "token-con-firma-invalida"));
        ServerRequest req = ServerRequest.create(servletRequest, List.of());
        when(jwtUtil.isValid("token-con-firma-invalida")).thenReturn(false);

        // ACT
        ServerResponse response = authFilter.filter(req, next);

        // ASSERT
        assertEquals(401, response.statusCode().value());
        verify(next, never()).handle(any());
    }

    @Test
    void filter_cookieValida_fijaXUserEmailDesdeElTokenYContinua() throws Exception {

        // ARRANGE
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        servletRequest.setCookies(new Cookie("sl_jwt", "token-valido"));
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        when(jwtUtil.isValid("token-valido")).thenReturn(true);
        when(jwtUtil.extractEmail("token-valido")).thenReturn("dueño@pyme.cl");
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        // ACT
        authFilter.filter(req, next);

        // ASSERT
        ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
        verify(next).handle(captor.capture());
        assertEquals("dueño@pyme.cl",
                captor.getValue().headers().firstHeader("X-User-Email"));
    }

    @Test
    void filter_headerXUserEmailForjadoConCookieValidaDeOtroUsuario_usaIdentidadDeLaCookie() throws Exception {

        // ARRANGE — regresión anti-spoofing: un atacante envía un
        // X-User-Email forjado junto con una cookie válida de OTRO usuario.
        // El header reenviado debe reflejar la identidad de la cookie, no la forjada.
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        servletRequest.addHeader("X-User-Email", "atacante-forjado@evil.cl");
        servletRequest.setCookies(new Cookie("sl_jwt", "token-valido"));
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        when(jwtUtil.isValid("token-valido")).thenReturn(true);
        when(jwtUtil.extractEmail("token-valido")).thenReturn("victima-real@pyme.cl");
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        // ACT
        authFilter.filter(req, next);

        // ASSERT
        ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
        verify(next).handle(captor.capture());
        assertEquals("victima-real@pyme.cl",
                captor.getValue().headers().firstHeader("X-User-Email"));
        assertEquals(1, captor.getValue().headers().header("X-User-Email").size());
    }
}
