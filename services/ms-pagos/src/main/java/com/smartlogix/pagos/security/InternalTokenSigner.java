package com.smartlogix.pagos.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * Mismo algoritmo que FlowService.firmar: Mac HmacSHA256 + SecretKeySpec,
 * aplicado aquí al tráfico este-oeste (ms-pagos → ms-pedidos) en vez de
 * al webhook de Flow. No debe confundirse con FlowService: usa un
 * secreto distinto (INTERNAL_SERVICE_SECRET, no FLOW_SECRET_KEY).
 */
@Component
public class InternalTokenSigner {

    private static final String SERVICE_NAME = "ms-pagos";

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
