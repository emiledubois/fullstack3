package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.RectificacionInternaDTO;
import com.ecommerce.api.gateway.dto.RectificacionResponseDTO;
import com.ecommerce.api.gateway.dto.RectificarEmailRequest;
import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * WebHub / Backend for Frontend (BFF) — Derecho de Rectificación ARCO+
 *
 * PUT /api/usuarios/me/email
 *
 * Corrige el email de la cuenta del USUARIO QUE LLAMA — identidad derivada
 * exclusivamente de la cookie sl_jwt verificada, nunca de un campo del body
 * (mismo anti-IDOR que UsuarioDatosController ya cierra para lecturas, ver
 * arco-remaining-rights.md §4.1/§7 A01). El chequeo de la contraseña actual
 * y la unicidad de emailNuevo solo pueden hacerse en auth-service (único
 * dueño de User.password/UserRepository) — este controller solo reenvía la
 * petición a PUT /auth/interno/usuarios/{email}, con {email} SIEMPRE
 * derivado server-side, nunca del body/path del cliente.
 *
 * Auditoría (§7 A09): el único log de esta operación vive en
 * AuthService.rectificarEmail (auth-service), que es quien realmente conoce
 * el resultado (éxito/rechazo) — este controller no duplica esa línea.
 */
@RestController
@RequestMapping("/usuarios")
public class RectificacionController {

    private static final String COOKIE_NAME = "sl_jwt";

    private final JwtUtil jwtUtil;
    private final WebClient authClient;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    public RectificacionController(
            JwtUtil jwtUtil,
            @Value("${auth.service.url:http://auth-service:8081}") String authUrl,
            WebClient.Builder builder,
            InternalTokenSigner internalTokenSigner) {
        this.jwtUtil = jwtUtil;
        this.authClient = builder.clone().baseUrl(authUrl).filter(internalTokenSigner.exchangeFilter()).build();
    }

    @PutMapping("/me/email")
    public ResponseEntity<?> rectificarEmail(
            @Valid @RequestBody RectificarEmailRequest req,
            HttpServletRequest request) {

        String token = extractToken(request);
        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String emailActual = jwtUtil.extractEmail(token);

        try {
            RectificacionInternaDTO interno = authClient.put()
                .uri("/auth/interno/usuarios/{email}", emailActual)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(RectificacionInternaDTO.class)
                .block();

            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, interno.getToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(jwtUtil.getRemainingSeconds(interno.getToken())))
                .build();

            RectificacionResponseDTO body = new RectificacionResponseDTO(
                interno.getEmail(), interno.getRole(), interno.getCuentaCreadaEn(), LocalDateTime.now());

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);

        } catch (WebClientResponseException e) {
            // El error real (contraseña incorrecta / email duplicado / cuenta
            // no encontrada / rate limit) ya fue determinado y logueado por
            // auth-service — el gateway solo repropaga status + mensaje tal
            // cual, sin reinterpretarlo ni loguear la petición fallida de
            // nuevo (evitaría duplicar la auditoría de §7 A09).
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
