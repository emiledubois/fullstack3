package com.ecommerce.api.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request de POST /api/usuarios/me/cancelacion — derecho de cancelación
// ARCO+ (arco-cancelacion-oposicion.md §6.1). Deliberadamente NO tiene
// ningún campo de identidad de cuenta objetivo: la cuenta a cancelar es
// siempre la de la cookie sl_jwt verificada, nunca un valor de este body.
// "confirmacion" se valida por igualdad EXACTA (case-sensitive) con el
// literal "ELIMINAR_MI_CUENTA" en el controller, no vía Bean Validation
// (@Pattern), para poder devolver un mensaje de error específico — solo
// @NotBlank aquí.
@Data
public class CancelacionRequest {

    @NotBlank(message = "La contraseña actual no puede estar vacía")
    private String passwordActual;

    @NotBlank(message = "Debe confirmar la cancelación")
    private String confirmacion;
}
