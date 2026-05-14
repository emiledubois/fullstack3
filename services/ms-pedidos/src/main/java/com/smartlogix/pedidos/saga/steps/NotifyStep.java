package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.saga.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component @Slf4j
public class NotifyStep implements SagaStep {

    private final WebClient.Builder webClientBuilder;

    @Value("${notification.service.url}")
    private String notificationUrl;

    public NotifyStep(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override public String getName() { return "NOTIFICAR"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        // NO crítico: si falla, la saga igual se considera exitosa.
        // Por eso NO lanzamos SagaStepException — solo logueamos.
        try {
            webClientBuilder.build()
                .post()
                .uri(notificationUrl + "/notificaciones")
                .bodyValue(Map.of(
                    "pedidoId",  ctx.getPedidoId(),
                    "envioId",   ctx.getEnvioId() != null ? ctx.getEnvioId() : 0L,
                    "userEmail", ctx.getRequest().getUserEmail() != null
                                  ? ctx.getRequest().getUserEmail() : "sin-email",
                    "sagaId",    ctx.getSagaId().toString(),
                    "mensaje",   "Pedido #" + ctx.getPedidoId() + " confirmado con envío #" + ctx.getEnvioId()
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
            log.info("[Saga {}] Notificación enviada", ctx.getSagaId());
        } catch (Exception e) {
            // No crítico: registrar y continuar — NO lanzar SagaStepException
            log.warn("[Saga {}] Notificación falló (no crítico, saga sigue exitosa): {}",
                     ctx.getSagaId(), e.getMessage());
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        // Sin compensación real para notificaciones
        log.info("[Saga {}] Paso NOTIFICAR: sin compensación aplicable", ctx.getSagaId());
    }
}
