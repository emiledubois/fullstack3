package com.smartlogix.pagos.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Firma como ms-pagos las llamadas salientes del RestTemplate de
 * PagoService hacia ms-pedidos (confirmar-pago / pago-fallido).
 * Defensa en profundidad: elimina cualquier cabecera interna
 * pre-existente en la petición antes de fijar la propia.
 */
@Component
@RequiredArgsConstructor
public class InternalAuthInterceptor implements ClientHttpRequestInterceptor {

    private final InternalTokenSigner signer;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().remove("X-Internal-Service");
        request.getHeaders().remove("X-Internal-Timestamp");
        request.getHeaders().remove("X-Internal-Signature");
        for (Map.Entry<String, String> header : signer.headers().entrySet()) {
            request.getHeaders().set(header.getKey(), header.getValue());
        }
        return execution.execute(request, body);
    }
}
