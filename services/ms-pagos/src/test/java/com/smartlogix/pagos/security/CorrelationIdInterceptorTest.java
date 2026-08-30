package com.smartlogix.pagos.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdInterceptorTest {

    private final CorrelationIdInterceptor interceptor = new CorrelationIdInterceptor();

    @Mock private HttpRequest request;
    @Mock private ClientHttpRequestExecution execution;
    @Mock private ClientHttpResponse expectedResponse;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void intercept_conCorrelationIdEnMdc_fijaLaCabeceraYEjecutaLaPeticion() throws Exception {

        // ARRANGE
        MDC.put("correlationId", "abc-123");
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        byte[] body = new byte[0];
        when(execution.execute(request, body)).thenReturn(expectedResponse);

        // ACT
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        // ASSERT
        assertEquals("abc-123", headers.getFirst("X-Correlation-Id"));
        assertSame(expectedResponse, result);
    }

    @Test
    void intercept_sinCorrelationIdEnMdc_noFijaLaCabeceraYEjecutaIgual() throws Exception {

        // ARRANGE — llamador que no pasó por CorrelationIdFilter (ninguno hoy,
        // pero no debe romper la llamada saliente). getHeaders() no se
        // stubea: sin correlationId en MDC, el interceptor no debe ni
        // consultar las cabeceras de la petición.
        byte[] body = new byte[0];
        when(execution.execute(request, body)).thenReturn(expectedResponse);

        // ACT
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        // ASSERT
        assertSame(expectedResponse, result);
    }
}
