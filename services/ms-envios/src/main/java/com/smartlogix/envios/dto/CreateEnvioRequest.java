package com.smartlogix.envios.dto;

import lombok.Data;

@Data
public class CreateEnvioRequest {
    private Long   pedidoId;
    private String tipoEnvio;      // "TERRESTRE" o "EXPRESS"
    private String transportista;
    private String destino;
}
