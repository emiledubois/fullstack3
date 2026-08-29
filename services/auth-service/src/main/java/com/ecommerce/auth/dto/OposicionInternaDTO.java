package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Respuesta de PUT /auth/interno/usuarios/{email}/oposicion (diseño §6.5).
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OposicionInternaDTO {
    private Boolean oposicionProcesamiento;
    private LocalDateTime oposicionRegistradaEn;
}
