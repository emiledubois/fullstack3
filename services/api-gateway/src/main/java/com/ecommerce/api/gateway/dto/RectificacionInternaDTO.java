package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Deserializa la respuesta de PUT /auth/interno/usuarios/{email}. Primera
// vez que un DTO interno de este codebase lleva un token de sesión vivo
// (arco-remaining-rights.md §4.2/§7 A02) — deliberadamente NO usa @Data: su
// toString() generado expondría el JWT completo si el objeto se loguea por
// error en algún punto. toString() propio enmascara el token igual que
// PagoService enmascara el token de Flow (token.substring(0,8)+"***").
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RectificacionInternaDTO {
    private String email;
    private String role;
    private LocalDateTime cuentaCreadaEn;
    private String token;

    @Override
    public String toString() {
        String tokenEnmascarado = token != null && token.length() > 8
                ? token.substring(0, 8) + "***"
                : "***";
        return "RectificacionInternaDTO(email=" + email + ", role=" + role + ", token=" + tokenEnmascarado + ")";
    }
}
