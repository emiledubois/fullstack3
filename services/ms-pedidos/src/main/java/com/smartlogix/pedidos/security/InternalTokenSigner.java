package com.smartlogix.pedidos.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Firma HMAC-SHA256 de las cabeceras de autenticación interna
 * (X-Internal-Service / X-Internal-Timestamp / X-Internal-Signature).
 *
 * Mismo algoritmo que FlowService.firmar (ms-pagos): Mac HmacSHA256 +
 * SecretKeySpec, aplicado aquí al tráfico este-oeste. ms-pedidos firma
 * como emisor (WebClients de la Saga hacia ms-inventario/ms-envios/
 * notification-service) y verifica como receptor (InternalAuthFilter).
 */
@Component
public class InternalTokenSigner {

    private static final String SERVICE_NAME = "ms-pedidos";

    @Value("${internal.service.secret}")
    private String secret;

    @PostConstruct
    public void validarConfiguracion() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_SECRET no puede estar vacío");
        }
    }

    public Map<String, String> headers() {
        long timestamp = System.currentTimeMillis();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Internal-Service", SERVICE_NAME);
        headers.put("X-Internal-Timestamp", String.valueOf(timestamp));
        headers.put("X-Internal-Signature", sign(timestamp));
        return headers;
    }

    public ExchangeFilterFunction exchangeFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            Map<String, String> signed = headers();
            ClientRequest signedRequest = ClientRequest.from(request)
                .headers(h -> {
                    h.remove("X-Internal-Service");
                    h.remove("X-Internal-Timestamp");
                    h.remove("X-Internal-Signature");
                    signed.forEach(h::set);
                })
                .build();
            return Mono.just(signedRequest);
        });
    }

    String sign(long timestamp) {
        try {
            String stringToSign = SERVICE_NAME + ":" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("[InternalAuth] Error firmando petición interna", e);
        }
    }
}
