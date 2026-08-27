package com.ecommerce.auth.security;

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
 * Verifica la autenticación interna servicio-a-servicio en /auth/interno/**.
 * Primera vez que auth-service actúa como receptor de auth interno (antes
 * nadie lo llamaba internamente — ver internal-service-auth.md §7). Único
 * llamador permitido: api-gateway (GET /auth/interno/usuarios/{email},
 * ver arco-acceso-personal-data.md §3.2). Se ejecuta con
 * @Order(HIGHEST_PRECEDENCE), antes que la cadena de Spring Security, así
 * que una petición sin credencial interna válida nunca llega a
 * SecurityConfig ni al controller.
 *
 * A diferencia de ms-pedidos/ms-envios/ms-inventario/notification-service
 * (donde ESTE filtro guarda TODOS los paths porque esos servicios no tienen
 * ningún endpoint público), auth-service sí expone endpoints públicos
 * (/auth/login, /auth/register, /auth/logout) que el gateway proxea SIN
 * cabeceras internas (authRoute() no aplica internalTokenIssuerFilter). Por
 * eso este filtro se restringe explícitamente a /auth/interno/** — aplicarlo
 * a todo el servicio rompería el login/registro existente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class InternalAuthFilter implements Filter {

    private static final long TTL_MILLIS = 30_000;

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

        if (!request.getRequestURI().startsWith("/auth/interno/")) {
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
