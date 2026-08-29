package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Deserializa la respuesta de PUT /auth/interno/usuarios/{email}/cancelacion
// /{iniciar,revertir,finalizar} — "id" es el identificador numérico estable
// que este gateway captura en la respuesta de "iniciar" y reenvía a
// "finalizar" (?id=), precisamente para que auth-service NO tenga que
// re-resolver por email en ese último paso bajo una carrera de doble-submit
// (arco-cancelacion-oposicion.md §7 A04).
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CancelacionInternaDTO {
    private Long id;
    private String email;
    private String status;
    private LocalDateTime cancelacionSolicitadaEn;
    private LocalDateTime cancelacionCompletadaEn;
}
