package com.smartlogix.pedidos.saga;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class SagaResultado {
    private UUID    sagaId;
    private boolean exitoso;
    private Long    pedidoId;
    private Long    envioId;
    private String  error;

    public static SagaResultado exito(UUID sagaId, Long pedidoId, Long envioId) {
        return SagaResultado.builder()
            .sagaId(sagaId).exitoso(true)
            .pedidoId(pedidoId).envioId(envioId)
            .build();
    }

    public static SagaResultado fallo(UUID sagaId, String error) {
        return SagaResultado.builder()
            .sagaId(sagaId).exitoso(false).error(error)
            .build();
    }
}
