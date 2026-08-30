package com.smartlogix.pagos.security;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Propaga el X-Correlation-Id en las llamadas salientes del RestTemplate de
 * PagoService hacia ms-pedidos (confirmar-pago / pago-fallido).
 * RestTemplate nunca cambia de hilo, así que leer MDC.get() aquí es seguro
 * — siempre que el llamador haya fijado el MDC en el mismo hilo que ejecuta
 * este interceptor (ver PagoService.confirmarPagoEnPedidos/
 * notificarPagoFallido, que restauran el MDC recibido por parámetro antes
 * de esta llamada porque ambos son @Async y arrancan en un hilo nuevo sin
 * el MDC del hilo que atendió el webhook — observability-correlation-ids.md
 * §5.3).
 */
@Component
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            request.getHeaders().set("X-Correlation-Id", correlationId);
        }
        return execution.execute(request, body);
    }
}
