package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.RectificarEmailInternoRequest;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.EmailYaRegistradoException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.UsuarioNoEncontradoException;
import com.ecommerce.auth.service.AuthService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET/PUT /auth/interno/usuarios/{email} — no enrutable vía /api/** (excluido
 * en GatewayConfig.authRoute(), ver arco-acceso-personal-data.md §3.2/§5).
 * Solo alcanzable por api-gateway, verificado por InternalAuthFilter, que ya
 * guarda todo /auth/interno/** por prefijo de path sin importar el método
 * HTTP — el PUT de rectificación no requirió ningún cambio en ese filtro
 * (arco-remaining-rights.md §4.2).
 */
@RestController
@RequestMapping("/auth/interno")
@RequiredArgsConstructor
public class UsuarioInternoController {

    private final AuthService authService;

    @GetMapping("/usuarios/{email}")
    public ResponseEntity<UsuarioInternoDTO> getUsuario(@PathVariable String email) {
        return authService.buscarUsuarioInterno(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Derecho de rectificación ARCO+ — el hop donde realmente se verifica la
    // contraseña actual y la unicidad de emailNuevo (auth-service es el único
    // dueño de User.password/UserRepository, ver diseño §4.2/§6).
    @PutMapping("/usuarios/{email}")
    @RateLimiter(name = "rectificacionRateLimiter", fallbackMethod = "rectificarEmailRateLimitFallback")
    public ResponseEntity<RectificacionInternaDTO> rectificarEmail(
            @PathVariable String email,
            @Valid @RequestBody RectificarEmailInternoRequest req) {
        RectificacionInternaDTO dto = authService.rectificarEmail(email, req);
        return ResponseEntity.ok(dto);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(EmailYaRegistradoException.class)
    public ResponseEntity<Map<String, String>> handleEmailYaRegistrado(EmailYaRegistradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleUsuarioNoEncontrado(UsuarioNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    // Se ejecuta automáticamente cuando rectificacionRateLimiter supera 5
    // intentos/60s — mitigación del password-oracle descrito en el diseño
    // §7 A04 (misma forma que AuthController.loginRateLimitFallback).
    public ResponseEntity<Map<String, String>> rectificarEmailRateLimitFallback(
            String email, RectificarEmailInternoRequest req, RequestNotPermitted e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(Map.of("error", "Demasiados intentos de rectificación. Espere 60 segundos."));
    }
}
