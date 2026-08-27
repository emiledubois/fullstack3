package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

// Deserializa la respuesta de GET /auth/interno/usuarios/{email} — nunca
// tiene un campo password (auth-service ya devuelve un DTO dedicado).
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CuentaDTO {
    private String        email;
    private String        role;
    private LocalDateTime cuentaCreadaEn;
}
