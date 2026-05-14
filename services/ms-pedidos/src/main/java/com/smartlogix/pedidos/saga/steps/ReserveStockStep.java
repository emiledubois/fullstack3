package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.saga.*;
import com.smartlogix.pedidos.saga.client.InventarioSagaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReserveStockStep implements SagaStep {

    private final InventarioSagaClient inventarioClient;

    @Override
    public String getName() { return "RESERVAR_STOCK"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        Long productoId = ctx.getRequest().getProductoId();
        int cantidad    = ctx.getRequest().getCantidad() != null ? ctx.getRequest().getCantidad() : 1;

        if (productoId == null) {
            // Sin producto específico, la saga avanza sin reserva
            log.info("[Saga {}] Sin productoId — omitiendo reserva de stock", ctx.getSagaId());
            return;
        }

        try {
            inventarioClient.reservarStock(productoId, cantidad, ctx.getSagaId().toString());
            ctx.setProductoId(productoId);
            ctx.setCantidadReservada(cantidad);
            log.info("[Saga {}] Stock reservado: productoId={}, cantidad={}", ctx.getSagaId(), productoId, cantidad);
        } catch (Exception e) {
            throw new SagaStepException(getName(), "Fallo al reservar stock: " + e.getMessage(), e);
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getProductoId() != null && ctx.getCantidadReservada() != null) {
            log.warn("[Saga {}] COMPENSACIÓN: Liberando stock productoId={}", ctx.getSagaId(), ctx.getProductoId());
            try {
                inventarioClient.liberarStock(
                    ctx.getProductoId(),
                    ctx.getCantidadReservada(),
                    ctx.getSagaId().toString()
                );
            } catch (Exception e) {
                // Loguear para revisión manual — no lanzar excepción desde compensate
                log.error("[Saga {}] ERROR en compensación de stock: {}", ctx.getSagaId(), e.getMessage());
            }
        }
    }
}
