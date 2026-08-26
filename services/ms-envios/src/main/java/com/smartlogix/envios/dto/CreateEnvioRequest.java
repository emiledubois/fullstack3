package com.smartlogix.envios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEnvioRequest {
    @NotNull
    private Long   pedidoId;

    @NotBlank
    private String tipoEnvio;      // "TERRESTRE" o "EXPRESS"

    @NotBlank
    private String transportista;

    @NotBlank
    private String destino;
}
