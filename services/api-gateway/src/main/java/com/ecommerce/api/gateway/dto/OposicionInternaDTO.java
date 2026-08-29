package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Deserializa la respuesta de PUT /auth/interno/usuarios/{email}/oposicion —
// mismos campos que la respuesta pública de POST /api/usuarios/me/oposicion
// (arco-cancelacion-oposicion.md §6.5), reenviada tal cual.
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OposicionInternaDTO {
    private Boolean oposicionProcesamiento;
    private LocalDateTime oposicionRegistradaEn;
}
