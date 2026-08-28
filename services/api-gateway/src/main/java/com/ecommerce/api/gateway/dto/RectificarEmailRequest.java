package com.ecommerce.api.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request de PUT /api/usuarios/me/email — derecho de rectificación ARCO+
// (arco-remaining-rights.md §4.1). Deliberadamente NO tiene ningún campo de
// identidad de cuenta objetivo (email/id/usuario): la cuenta a corregir es
// siempre la de la cookie sl_jwt verificada, nunca un valor de este body.
@Data
public class RectificarEmailRequest {

    @NotBlank(message = "El email nuevo no puede estar vacío")
    @Email(message = "El email nuevo no tiene un formato válido")
    private String emailNuevo;

    @NotBlank(message = "La contraseña actual no puede estar vacía")
    private String passwordActual;
}
