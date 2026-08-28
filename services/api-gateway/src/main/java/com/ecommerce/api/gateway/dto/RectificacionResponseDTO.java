package com.ecommerce.api.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

// Respuesta pública de PUT /api/usuarios/me/email — el JWT nuevo va en
// Set-Cookie (RectificacionController), nunca en este body.
@Data
@AllArgsConstructor
public class RectificacionResponseDTO {
    private String email;
    private String role;
    private LocalDateTime cuentaCreadaEn;
    private LocalDateTime actualizadoEn;
}
