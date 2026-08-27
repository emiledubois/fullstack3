package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

// Deserializa la respuesta de GET /pedidos/interno/por-email/{email}.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoDatosDTO {
    private Long          id;
    private String        clienteNombre;
    private Double        total;
    private String        status;
    private String        tipoPedido;
    private String        destino;
    private Long          productoId;
    private Integer       cantidad;
    private LocalDateTime creadoEn;
}
