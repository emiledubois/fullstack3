package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request de PUT /auth/interno/usuarios/{email}/cancelacion/iniciar —
// reenviado tal cual desde POST /api/usuarios/me/cancelacion. {email} en el
// path es SIEMPRE el email derivado server-side por api-gateway desde la
// cookie verificada — nunca viaja en este body. "confirmacion" se
// re-verifica aquí también (defensa en profundidad, diseño §6.2): nunca
// confiar en que la validación del gateway sea el único chequeo.
@Data
public class CancelacionInternoRequest {

    @NotBlank(message = "La contraseña actual no puede estar vacía")
    private String passwordActual;

    @NotBlank(message = "Debe confirmar la cancelación")
    private String confirmacion;
}
