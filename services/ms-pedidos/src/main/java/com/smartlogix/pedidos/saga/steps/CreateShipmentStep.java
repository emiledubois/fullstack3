package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.saga.*;
import com.smartlogix.pedidos.saga.client.EnviosSagaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CreateShipmentStep implements SagaStep {

    private final EnviosSagaClient enviosClient;

    @Override public String getName() { return "CREAR_ENVIO"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        try {
            String tipo    = "INTERNACIONAL".equals(ctx.getRequest().getTipoPedido())
                           ? "EXPRESS" : "TERRESTRE";
            String destino = ctx.getRequest().getDestino();
            Long envioId = enviosClient.crearEnvio(
                ctx.getPedidoId(), tipo, destino, ctx.getSagaId().toString());
            ctx.setEnvioId(envioId);
            log.info("[Saga {}] Envío creado: id={}", ctx.getSagaId(), envioId);
        } catch (Exception e) {
            throw new SagaStepException(getName(), "Fallo al crear envío: " + e.getMessage(), e);
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getEnvioId() != null) {
            log.warn("[Saga {}] COMPENSACIÓN Paso 3: cancelando envío id={}",
                     ctx.getSagaId(), ctx.getEnvioId());
            try {
                enviosClient.cancelarEnvio(ctx.getEnvioId(), ctx.getSagaId().toString());
            } catch (Exception e) {
                log.error("[Saga {}] Error en compensación de envío: {}", ctx.getSagaId(), e.getMessage());
            }
        }
    }
}
