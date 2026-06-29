package com.smartlogix.pagos.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servicio de integración con la API REST de Flow.
 *
 * Documentación oficial: https://developers.flow.cl/en/docs/intro
 *
 * Endpoints utilizados:
 *   POST /payment/create    — Crear orden de pago
 *   GET  /payment/getStatus — Verificar estado de un pago
 *
 * Algoritmo de firma HMAC-SHA256:
 *   1. Ordenar parámetros alfabéticamente
 *   2. Concatenar como clave+valor sin separador
 *   3. Firmar con HMAC-SHA256 usando secretKey
 *   4. Agregar resultado como parámetro "s"
 */
@Service
@Slf4j
public class FlowService {

    @Value("${flow.api.url}")    private String flowUrl;
    @Value("${flow.api.key}")    private String apiKey;
    @Value("${flow.api.secret}") private String secretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void validarConfiguracion() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("TU_")) {
            throw new IllegalStateException("FLOW_API_KEY no configurado correctamente");
        }
        if (secretKey == null || secretKey.isBlank() || secretKey.startsWith("TU_")) {
            throw new IllegalStateException("FLOW_SECRET_KEY no configurado correctamente");
        }
        log.info("[Flow] Configuración validada correctamente");
    }

    // Crear orden de pago en Flow 
    // Fuente: developers.flow.cl — Creación de orden
    // Content-Type: application/x-www-form-urlencoded (obligatorio)
    public FlowCreateResponse crearOrden(
            String commerceOrder,
            String subject,
            Integer amount,
            String email,
            String urlConfirmation,
            String urlReturn) {

        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey",          apiKey);
        params.put("commerceOrder",   commerceOrder);
        params.put("subject",         subject);
        params.put("amount",          String.valueOf(amount));
        params.put("email",           email);
        params.put("urlConfirmation", urlConfirmation);
        params.put("urlReturn",       urlReturn);
        params.put("currency",        "CLP");
        params.put("paymentMethod",   "9"); // 9 = Todos los medios de pago

        // Agregar firma HMAC-SHA256
        // Fuente: developers.flow.cl — Primeros pasos (Firma de parámetros)
        params.put("s", firmar(params));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        params.forEach(body::add);

        log.info("[Flow] Creando orden de pago: commerceOrder={}", commerceOrder);
        ResponseEntity<FlowCreateResponse> response = restTemplate.postForEntity(
            flowUrl + "/payment/create",
            new HttpEntity<>(body, headers),
            FlowCreateResponse.class
        );

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("[Flow] Error al crear orden: " + response.getStatusCode());
        }

        log.info("[Flow] Orden creada: flowOrder={}, token={}",
            response.getBody().getFlowOrder(), response.getBody().getToken());
        return response.getBody();
    }

    // Verificar estado del pago
    // Fuente: developers.flow.cl — Estado de orden
    // GET /payment/getStatus?apiKey=...&token=...&s=...
    // Status: 1=pendiente, 2=pagado, 3=rechazado, 4=anulado
    public FlowStatusResponse obtenerEstado(String token) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apiKey", apiKey);
        params.put("token",  token);
        params.put("s",      firmar(params));

        String url = flowUrl + "/payment/getStatus?apiKey={apiKey}&token={token}&s={s}";

        log.info("[Flow] Consultando estado para token={}", token);
        FlowStatusResponse resp = restTemplate.getForObject(url, FlowStatusResponse.class,
            params.get("apiKey"), params.get("token"), params.get("s"));

        if (resp == null) throw new RuntimeException("[Flow] Sin respuesta de getStatus");
        log.info("[Flow] Estado: flowOrder={}, status={}", resp.getFlowOrder(), resp.getStatus());
        return resp;
    }

    // Verificar firma del webhook entrante
    // Fuente: developers.flow.cl — Primeros pasos (Firma de parámetros)
    // Flow envía el token como POST. Verificar que viene de Flow firmando
    // con apiKey + token y comparando con el "s" del webhook.
    public boolean verificarFirmaWebhook(String token, String firmaRecibida) {
        Objects.requireNonNull(token, "token no puede ser null");
        Objects.requireNonNull(firmaRecibida, "firmaRecibida no puede ser null");
        try {
            String firmaEsperada = firmar(Map.of("apiKey", apiKey, "token", token));
            // Comparación de tiempo constante → evita timing attacks
            return MessageDigest.isEqual(
                 firmaEsperada.getBytes(StandardCharsets.UTF_8),
                 firmaRecibida.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("[Flow] Error verificando firma webhook: {}", e.getMessage());
            return false;
        }
    }

    // Algoritmo de firma HMAC-SHA256
    // Fuente: developers.flow.cl — Primeros pasos
    // "Ordenar parámetros alfabéticamente, concatenar clave+valor,
    //  firmar con HMAC-SHA256, resultado en hexadecimal."
    public String firmar(Map<String, String> params) {
        try {
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for (String key : keys) sb.append(key).append(params.get(key));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(
                sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("[Flow] Error al firmar parámetros", e);
        }
    }

    // DTOs internos para la respuesta de Flow
    @lombok.Data public static class FlowCreateResponse {
        private String url;         // URL base del checkout
        private String token;       // token de la transacción
        private Long   flowOrder;   // número de orden Flow
    }

    @lombok.Data public static class FlowStatusResponse {
        private Long   flowOrder;
        private String commerceOrder;
        private Integer status;     // 1=pend, 2=pagado, 3=rechazo, 4=anulado
        private Double  amount;
        private String  payer;
        private String  subject;
    }
}
