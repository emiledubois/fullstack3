package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import com.smartlogix.pedidos.saga.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CreateOrderStep implements SagaStep {

    private final OrderRepository orderRepository;
    private final PedidoFactory   pedidoFactory;

    @Override public String getName() { return "CREAR_PEDIDO"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        try {
            // Usa PedidoFactory (Factory Method ya implementado)
            Pedido pedido = pedidoFactory.crearPedido(ctx.getRequest());
            // Estado CONFIRMADO: el stock ya está reservado en el Paso 1
            pedido.setStatus("CONFIRMADO");
            Pedido saved = orderRepository.save(pedido);
            ctx.setPedidoId(saved.getId());
            log.info("[Saga {}] Pedido creado: id={}, tipo={}",
                     ctx.getSagaId(), saved.getId(), saved.getTipoPedido());
        } catch (Exception e) {
            throw new SagaStepException(getName(), "Fallo al crear pedido: " + e.getMessage(), e);
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getPedidoId() != null) {
            log.warn("[Saga {}] COMPENSACIÓN Paso 2: cancelando pedido id={}",
                     ctx.getSagaId(), ctx.getPedidoId());
            try {
                orderRepository.findById(ctx.getPedidoId()).ifPresent(p -> {
                    p.setStatus("CANCELADO");
                    p.setObservaciones("Cancelado por fallo en Saga " + ctx.getSagaId());
                    orderRepository.save(p);
                });
            } catch (Exception e) {
                log.error("[Saga {}] Error en compensación de pedido: {}", ctx.getSagaId(), e.getMessage());
            }
        }
    }
}
