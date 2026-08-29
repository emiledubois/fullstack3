package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Request de PUT /auth/interno/usuarios/{email}/oposicion — reenviado tal
// cual desde POST /api/usuarios/me/oposicion (diseño §6.5). No es un ratchet
// de un solo sentido: "oponerse" puede pasar de true a false libremente.
@Data
public class OposicionInternoRequest {

    @NotNull(message = "El campo oponerse es obligatorio")
    private Boolean oponerse;
}
