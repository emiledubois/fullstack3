package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.security.InternalTokenSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.*;

import java.util.Map;

/**
 * Emite las cabeceras de autenticación interna (X-Internal-Service /
 * X-Internal-Timestamp / X-Internal-Signature) en toda petición que el
 * gateway proxea hacia un microservicio downstream.
 *
 * Sigue el mismo patrón "strip-then-set" que AuthFilter usa con
 * X-User-Email: nunca confiar en un valor de estas cabeceras enviado por
 * el cliente, siempre eliminarlo y fijar el valor derivado (aquí, el
 * generado por InternalTokenSigner) antes de continuar la cadena.
 */
@Component
public class InternalTokenIssuerFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Autowired private InternalTokenSigner signer;

    @Override
    public ServerResponse filter(ServerRequest req, HandlerFunction<ServerResponse> next)
            throws Exception {
        Map<String, String> signed = signer.headers();

        ServerRequest modified = ServerRequest.from(req)
            .headers(headers -> {
                headers.remove("X-Internal-Service");
                headers.remove("X-Internal-Timestamp");
                headers.remove("X-Internal-Signature");
            })
            .header("X-Internal-Service", signed.get("X-Internal-Service"))
            .header("X-Internal-Timestamp", signed.get("X-Internal-Timestamp"))
            .header("X-Internal-Signature", signed.get("X-Internal-Signature"))
            .build();
        return next.handle(modified);
    }
}
