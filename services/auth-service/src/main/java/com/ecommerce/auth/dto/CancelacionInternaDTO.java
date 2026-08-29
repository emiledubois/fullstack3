package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Respuesta de PUT /auth/interno/usuarios/{email}/cancelacion/{iniciar,
// revertir,finalizar}. "id" es el identificador numérico estable de la fila
// — api-gateway lo captura en la respuesta de "iniciar" y lo reenvía en
// "finalizar" (query param ?id=), precisamente para NO tener que re-resolver
// por email en ese último paso (race de doble-submit, diseño §7 A04).
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelacionInternaDTO {
    private Long id;
    private String email;
    private String status;
    private LocalDateTime cancelacionSolicitadaEn;
    private LocalDateTime cancelacionCompletadaEn;
}
