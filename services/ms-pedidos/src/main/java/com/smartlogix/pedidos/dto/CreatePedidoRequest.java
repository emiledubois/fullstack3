package com.smartlogix.pedidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreatePedidoRequest {
    @NotNull
    private Long   userId;

    @NotBlank
    private String userEmail;

    @NotBlank
    private String clienteNombre;

    @NotNull @Positive
    private Double total;

    @NotBlank
    private String tipoPedido;   // "NACIONAL" o "INTERNACIONAL"

    @NotBlank
    private String destino;

    // Para verificación de stock con Circuit Breaker
    @NotNull
    private Long   productoId;

    @NotNull @Positive
    private Integer cantidad;
}
