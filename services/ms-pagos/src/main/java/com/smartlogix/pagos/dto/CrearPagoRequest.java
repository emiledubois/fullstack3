package com.smartlogix.pagos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CrearPagoRequest {
    @NotNull
    private Long   pedidoId;

    @NotNull @Positive
    private Double monto;

    @NotBlank @Email
    private String email;

    private String descripcion;   // subject en Flow
}
