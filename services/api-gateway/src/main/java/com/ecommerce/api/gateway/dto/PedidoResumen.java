package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoResumen {
    private Long   id;
    private String clienteNombre;
    private String userEmail;
    private Double total;
    private String status;
    private String tipoPedido;
    private String destino;
    private String creadoEn;
}
