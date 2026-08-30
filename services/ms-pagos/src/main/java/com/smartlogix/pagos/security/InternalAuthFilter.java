package com.smartlogix.pagos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Verifica la autenticación interna servicio-a-servicio en /pagos/**,
 * EXCEPTO /pagos/webhook/flow (Flow Chile es un caller externo que no
 * puede tener INTERNAL_SERVICE_SECRET; ese endpoint sigue protegido
 * únicamente por su propia firma HMAC de Flow, sin cambios).
 * Allowlist (§5.2): crear/consultar pago solo aceptan api-gateway.
 */
// Corre después de CorrelationIdFilter (HIGHEST_PRECEDENCE) para que un
// rechazo de este filtro también quede trazado con un correlationId — ver
// observability-correlation-ids.md §5.2.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class InternalAuthFilter implements Filter {

    private static final long TTL_MILLIS = 30_000;
    private static final String WEBHOOK_PATH = "/pagos/webhook/flow";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${internal.service.secret}")
    private String secret;

    @PostConstruct
    public void validarConfiguracion() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_SECRET no puede estar vacío");
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (WEBHOOK_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String service = request.getHeader("X-Internal-Service");
        String timestampHeader = request.getHeader("X-Internal-Timestamp");
        String signature = request.getHeader("X-Internal-Signature");

        if (service == null || timestampHeader == null || signature == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Autenticación interna requerida", request, service);
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Autenticación interna inválida", request, service);
            return;
        }

        if (Math.abs(System.currentTimeMillis() - timestamp) > TTL_MILLIS) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Credencial interna expirada", request, service);
            return;
        }

        String expected = sign(service, timestamp);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Firma interna inválida", request, service);
            return;
        }

        if (!"api-gateway".equals(service)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                "Servicio no autorizado para este recurso", request, service);
            return;
        }

        chain.doFilter(request, response);
    }

    private String sign(String service, long timestamp) {
        try {
            String stringToSign = service + ":" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("[InternalAuth] Error verificando firma interna", e);
        }
    }

    private void reject(HttpServletResponse response, int status, String message,
                         HttpServletRequest request, String claimedService) throws IOException {
        log.warn("[InternalAuth] Rechazado: servicio={}, path={}, timestamp={}, motivo={}",
            claimedService, request.getRequestURI(), Instant.now(), message);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }
}
