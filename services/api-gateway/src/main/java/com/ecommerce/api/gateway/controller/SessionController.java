package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.SessionDTO;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * GET /api/session
 *
 * Permite al frontend preguntarle al servidor "¿estoy autenticado?" ahora que
 * el JWT vive en una cookie httpOnly y ya no es legible desde JS. AuthFilter
 * ya validó la cookie antes de que la petición llegue aquí (WebHub/BFF,
 * mismo patrón que DashboardController) — no hay llamada a auth-service.
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private static final String COOKIE_NAME = "sl_jwt";

    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<SessionDTO> getSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String token = cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(new SessionDTO(jwtUtil.extractEmail(token), jwtUtil.extractRole(token)));
    }
}
