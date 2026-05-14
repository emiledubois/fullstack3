package com.smartlogix.pedidos.saga;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

/**
 * Contexto mutable que viaja por todos los pasos de la Saga.
 * Cada paso lo enriquece con los IDs creados.
 */
@Data
@Builder
public class SagaContext {
    private UUID sagaId;
    private CreatePedidoRequest request;

    // Resultado del paso 1 (reserva de stock)
    private Long productoId;
    private Integer cantidadReservada;

    // Resultado del paso 2 (pedido creado)
    private Long pedidoId;

    // Resultado del paso 3 (envío creado)
    private Long envioId;
}
