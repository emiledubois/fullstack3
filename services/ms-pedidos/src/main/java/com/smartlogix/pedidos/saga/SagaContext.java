package com.smartlogix.pedidos.saga;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

/**
 * Contexto mutable que viaja entre todos los pasos de la Saga.
 * Cada paso lo enriquece con los IDs generados.
 *
 * Flujo:
 *   Paso 1 -> escribe productoId + cantidadReservada
 *   Paso 2 -> escribe pedidoId
 *   Paso 3 -> escribe envioId
 *   Paso 4 -> solo lee para notificar
 */
@Data
@Builder
public class SagaContext {
    private UUID                sagaId;
    private CreatePedidoRequest request;

    // Resultado del Paso 1
    private Long    productoId;
    private Integer cantidadReservada;

    // Resultado del Paso 2
    private Long pedidoId;

    // Resultado del Paso 3
    private Long envioId;
}
