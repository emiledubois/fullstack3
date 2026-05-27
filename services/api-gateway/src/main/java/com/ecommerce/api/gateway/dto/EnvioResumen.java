package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvioResumen {
    private Long   id;
    private Long   pedidoId;
    private String status;
    private String tipoEnvio;
    private String transportista;
    private String destino;
    private String creadoEn;
}
