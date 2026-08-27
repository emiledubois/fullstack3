package com.smartlogix.pagos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalAuthInterceptorTest {

    private InternalTokenSigner signer;
    private InternalAuthInterceptor interceptor;

    @Mock private HttpRequest request;
    @Mock private ClientHttpRequestExecution execution;
    @Mock private ClientHttpResponse expectedResponse;

    @BeforeEach
    void setUp() {
        signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", "shared-internal-secret");
        interceptor = new InternalAuthInterceptor(signer);
    }

    @Test
    void intercept_fijaLasTresCabecerasFirmadasYEjecutaLaPeticion() throws Exception {

        // ARRANGE
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        byte[] body = new byte[0];
        when(execution.execute(request, body)).thenReturn(expectedResponse);

        // ACT
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        // ASSERT
        assertEquals("ms-pagos", headers.getFirst("X-Internal-Service"));
        assertNotNull(headers.getFirst("X-Internal-Timestamp"));
        assertNotNull(headers.getFirst("X-Internal-Signature"));
        assertSame(expectedResponse, result);
        verify(execution).execute(request, body);
    }

    @Test
    void intercept_eliminaCabecerasInternasPreexistentes() throws Exception {

        // ARRANGE — defensa en profundidad: una cabecera interna
        // ya presente en la petición antes de pasar por el interceptor
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "servicio-forjado");
        when(request.getHeaders()).thenReturn(headers);
        byte[] body = new byte[0];
        when(execution.execute(any(), any())).thenReturn(expectedResponse);

        // ACT
        interceptor.intercept(request, body, execution);

        // ASSERT
        assertEquals("ms-pagos", headers.getFirst("X-Internal-Service"));
        assertEquals(1, headers.get("X-Internal-Service").size());
    }
}
