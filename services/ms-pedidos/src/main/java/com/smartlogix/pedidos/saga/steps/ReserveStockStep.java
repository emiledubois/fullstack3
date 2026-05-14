package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.saga.*;
import com.smartlogix.pedidos.saga.client.InventarioSagaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class ReserveStockStep implements SagaStep {

    private final InventarioSagaClient inventarioClient;

    @Override public String getName() { return "RESERVAR_STOCK"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        Long productoId = ctx.getRequest().getProductoId();
        if (productoId == null) {
            // Sin producto: omitir reserva (pedido sin verificación de stock)
            log.info("[Saga {}] Sin productoId — omitiendo reserva de stock", ctx.getSagaId());
            return;
        }
        int cantidad = ctx.getRequest().getCantidad() != null ? ctx.getRequest().getCantidad() : 1;
        try {
            inventarioClient.reservarStock(productoId, cantidad, ctx.getSagaId().toString());
            ctx.setProductoId(productoId);
            ctx.setCantidadReservada(cantidad);
        } catch (Exception e) {
            throw new SagaStepException(getName(), "Fallo al reservar stock: " + e.getMessage(), e);
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getProductoId() != null && ctx.getCantidadReservada() != null) {
            log.warn("[Saga {}] COMPENSACIÓN Paso 1: liberando stock productoId={}",
                     ctx.getSagaId(), ctx.getProductoId());
            try {
                inventarioClient.liberarStock(
                    ctx.getProductoId(),
                    ctx.getCantidadReservada(),
                    ctx.getSagaId().toString());
            } catch (Exception e) {
                // compensate() absorbe errores — el orquestador siempre continúa compensando
                log.error("[Saga {}] Error en compensación de stock: {}", ctx.getSagaId(), e.getMessage());
            }
        }
    }
}
