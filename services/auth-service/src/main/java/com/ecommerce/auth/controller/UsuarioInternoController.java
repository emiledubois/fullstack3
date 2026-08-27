package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /auth/interno/usuarios/{email} — no enrutable vía /api/** (excluido
 * en GatewayConfig.authRoute(), ver arco-acceso-personal-data.md §3.2/§5).
 * Solo alcanzable por api-gateway, verificado por InternalAuthFilter.
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
}
