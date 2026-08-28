package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// Respuesta de PUT /auth/interno/usuarios/{email} — primera vez que un DTO
// interno de este codebase lleva un token de sesión vivo (arco-remaining-
// rights.md §4.2/§7 A02). Deliberadamente NO usa @Data/@ToString generado:
// su toString() propio enmascara el token igual que PagoService enmascara el
// token de Flow (token.substring(0,8)+"***"), para que un log accidental del
// objeto completo nunca exponga el JWT.
@Getter
@AllArgsConstructor
public class RectificacionInternaDTO {

    private final String email;
    private final String role;
    private final LocalDateTime cuentaCreadaEn;
    private final String token;

    @Override
    public String toString() {
        String tokenEnmascarado = token != null && token.length() > 8
                ? token.substring(0, 8) + "***"
                : "***";
        return "RectificacionInternaDTO(email=" + email + ", role=" + role
                + ", cuentaCreadaEn=" + cuentaCreadaEn + ", token=" + tokenEnmascarado + ")";
    }
}
