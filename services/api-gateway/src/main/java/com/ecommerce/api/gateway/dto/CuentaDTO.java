package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

// Deserializa la respuesta de GET /auth/interno/usuarios/{email} — nunca
// tiene un campo password (auth-service ya devuelve un DTO dedicado).
//
// oposicionProcesamiento/oposicionRegistradaEn (arco-cancelacion-oposicion.md
// §6.6): permiten que el titular verifique su propia oposición registrada vía
// GET /api/usuarios/me/datos — de lo contrario el flag sería de solo-escritura.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CuentaDTO {
    private String        email;
    private String        role;
    private LocalDateTime cuentaCreadaEn;
    private boolean        oposicionProcesamiento;
    private LocalDateTime oposicionRegistradaEn;
}
