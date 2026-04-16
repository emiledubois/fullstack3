package com.smartlogix.pedidos.dto;

import lombok.Data;


@Data
public class CreatePedidoRequest {
    private Long   userId;
    private String userEmail;
    private String clienteNombre;
    private Double total;
    private String tipoPedido;   // "NACIONAL" o "INTERNACIONAL"
    private String destino;
    // Para verificación de stock con Circuit Breaker
    private Long   productoId;
    private Integer cantidad;
}

