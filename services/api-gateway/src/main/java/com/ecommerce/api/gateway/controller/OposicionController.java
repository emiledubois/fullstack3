package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.OposicionInternaDTO;
import com.ecommerce.api.gateway.dto.OposicionRequest;
import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Arrays;

/**
 * WebHub / Backend for Frontend (BFF) — Derecho de Oposición ARCO+
 *
 * POST /api/usuarios/me/oposicion
 *
 * Mutación de un solo servicio (gateway → auth-service, sin compensación) —
 * misma forma que Rectificación, no un Facade/Saga (arco-cancelacion-
 * oposicion.md §5). Sin re-autenticación por contraseña ni rate limiter: es
 * un toggle de preferencia totalmente reversible, sin consecuencia
 * destructiva — una sesión válida basta (§6.5).
 *
 * Honestidad del diseño (§1/§6.5): hoy este flag NO gatilla ningún cambio de
 * tratamiento de datos — no existe tratamiento opcional/de interés legítimo
 * activo en este sistema (notification-service es contract-necessary, no
 * opcional). Su valor es un registro auditable + un checkpoint estructural
 * para cualquier tratamiento opcional futuro.
 */
@RestController
@RequestMapping("/usuarios")
public class OposicionController {

    private static final String COOKIE_NAME = "sl_jwt";

    private final JwtUtil jwtUtil;
    private final WebClient authClient;

    public OposicionController(
            JwtUtil jwtUtil,
            @Value("${auth.service.url:http://auth-service:8081}") String authUrl,
            WebClient.Builder builder,
            InternalTokenSigner internalTokenSigner) {
        this.jwtUtil = jwtUtil;
        this.authClient = builder.clone().baseUrl(authUrl).filter(internalTokenSigner.exchangeFilter()).build();
    }

    @PostMapping("/me/oposicion")
    public ResponseEntity<?> registrarOposicion(
            @Valid @RequestBody OposicionRequest req,
            HttpServletRequest request) {

        String token = extractToken(request);
        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String email = jwtUtil.extractEmail(token);

        try {
            OposicionInternaDTO dto = authClient.put()
                    .uri("/auth/interno/usuarios/{email}/oposicion", email)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(OposicionInternaDTO.class)
                    .block();

            return ResponseEntity.ok(dto);
        } catch (WebClientResponseException e) {
            // 404 — cookie válida pero la cuenta ya no resuelve (incluye una
            // cuenta ya cancelada) — auth-service ya determinó el motivo.
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsByteArray());
        }
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
