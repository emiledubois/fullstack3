package com.ecommerce.api.gateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Request de POST /api/usuarios/me/oposicion — derecho de oposición ARCO+
// (arco-cancelacion-oposicion.md §6.5). No es un ratchet de un solo
// sentido: "oponerse" puede pasar de true a false libremente.
@Data
public class OposicionRequest {

    @NotNull(message = "El campo oponerse es obligatorio")
    private Boolean oponerse;
}
