package com.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request de PUT /auth/interno/usuarios/{email} — reenviado tal cual desde
// PUT /api/usuarios/me/email (arco-remaining-rights.md §4.2). {email} en el
// path es SIEMPRE el email actual derivado server-side por api-gateway desde
// la cookie verificada — nunca viaja en este body.
@Data
public class RectificarEmailInternoRequest {

    @NotBlank(message = "El email nuevo no puede estar vacío")
    @Email(message = "El email nuevo no tiene un formato válido")
    private String emailNuevo;

    @NotBlank(message = "La contraseña actual no puede estar vacía")
    private String passwordActual;
}
