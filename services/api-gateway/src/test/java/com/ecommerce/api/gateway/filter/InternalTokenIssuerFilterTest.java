package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.security.InternalTokenSigner;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalTokenIssuerFilterTest {

    @Mock private InternalTokenSigner signer;
    @InjectMocks private InternalTokenIssuerFilter filter;

    @Mock private HandlerFunction<ServerResponse> next;

    @Test
    void filter_fijaLasTresCabecerasFirmadasYContinua() throws Exception {

        // ARRANGE
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/inventario");
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        when(signer.headers()).thenReturn(Map.of(
            "X-Internal-Service", "api-gateway",
            "X-Internal-Timestamp", "1000",
            "X-Internal-Signature", "abc123"
        ));
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        // ACT
        filter.filter(req, next);

        // ASSERT
        ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
        verify(next).handle(captor.capture());
        assertEquals("api-gateway", captor.getValue().headers().firstHeader("X-Internal-Service"));
        assertEquals("1000", captor.getValue().headers().firstHeader("X-Internal-Timestamp"));
        assertEquals("abc123", captor.getValue().headers().firstHeader("X-Internal-Signature"));
    }

    @Test
    void filter_cabecerasInternasForjadasPorElCliente_sonReemplazadasPorLasFirmadas() throws Exception {

        // ARRANGE — regresión anti-spoofing: un llamador envía sus propias
        // cabeceras X-Internal-* junto con la petición al gateway
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/pedidos");
        servletRequest.addHeader("X-Internal-Service", "atacante");
        servletRequest.addHeader("X-Internal-Timestamp", "0");
        servletRequest.addHeader("X-Internal-Signature", "forjada");
        ServerRequest req = ServerRequest.create(servletRequest, List.of());

        when(signer.headers()).thenReturn(Map.of(
            "X-Internal-Service", "api-gateway",
            "X-Internal-Timestamp", "2000",
            "X-Internal-Signature", "firma-real"
        ));
        when(next.handle(any())).thenReturn(ServerResponse.ok().build());

        // ACT
        filter.filter(req, next);

        // ASSERT
        ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
        verify(next).handle(captor.capture());
        assertEquals("api-gateway", captor.getValue().headers().firstHeader("X-Internal-Service"));
        assertEquals(1, captor.getValue().headers().header("X-Internal-Service").size());
        assertEquals("firma-real", captor.getValue().headers().firstHeader("X-Internal-Signature"));
    }
}
