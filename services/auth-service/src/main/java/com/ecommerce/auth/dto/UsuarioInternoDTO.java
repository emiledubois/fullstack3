package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Respuesta de GET /auth/interno/usuarios/{email} — deliberadamente NO es la
// entidad User (que trae password). Solo los campos que un data subject
// necesita ver de su propia cuenta (derecho de acceso, Ley 21.719).
//
// oposicionProcesamiento/oposicionRegistradaEn (diseño de cancelación/
// oposición §6.6): se exponen aquí también para que el derecho de acceso
// (GET /api/usuarios/me/datos) permita al titular verificar su propia
// oposición registrada — de lo contrario el flag sería de solo-escritura.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioInternoDTO {
    private String email;
    private String role;
    private LocalDateTime cuentaCreadaEn;
    private boolean oposicionProcesamiento;
    private LocalDateTime oposicionRegistradaEn;
}
