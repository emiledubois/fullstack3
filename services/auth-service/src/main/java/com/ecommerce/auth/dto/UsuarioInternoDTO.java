package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Respuesta de GET /auth/interno/usuarios/{email} — deliberadamente NO es la
// entidad User (que trae password). Solo los campos que un data subject
// necesita ver de su propia cuenta (derecho de acceso, Ley 21.719).
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioInternoDTO {
    private String email;
    private String role;
    private LocalDateTime cuentaCreadaEn;
}
