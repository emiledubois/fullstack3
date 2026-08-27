package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

// Deserializa la respuesta de GET /envios/interno/por-pedidos.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvioDatosDTO {
    private Long          id;
    private Long          pedidoId;
    private String        status;
    private String        tipoEnvio;
    private String        transportista;
    private String        destino;
    private LocalDateTime fechaEstimadaEntrega;
    private LocalDateTime creadoEn;
}
