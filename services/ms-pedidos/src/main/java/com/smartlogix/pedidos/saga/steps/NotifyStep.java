package com.smartlogix.pedidos.saga.steps;

import com.smartlogix.pedidos.saga.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotifyStep implements SagaStep {

    private final WebClient.Builder webClientBuilder;

    @Value("${notification.service.url}")
    private String notificationUrl;

    @Override
    public String getName() { return "NOTIFICAR"; }

    @Override
    public void execute(SagaContext ctx) throws SagaStepException {
        // La notificación es NO CRÍTICA: si falla, la saga sigue siendo exitosa.
        // Aplicamos best-effort con try/catch que NO lanza SagaStepException.
        try {
            webClientBuilder.build()
                .post()
                .uri(notificationUrl + "/notificaciones")
                .bodyValue(Map.of(
                    "userEmail", ctx.getRequest().getUserEmail() != null
                                 ? ctx.getRequest().getUserEmail() : "sin-email",
                    "pedidoId",  ctx.getPedidoId(),
                    "envioId",   ctx.getEnvioId(),
                    "sagaId",    ctx.getSagaId().toString(),
                    "mensaje",   "Tu pedido #" + ctx.getPedidoId() + " ha sido confirmado."
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
            log.info("[Saga {}] Notificación enviada a {}", ctx.getSagaId(), ctx.getRequest().getUserEmail());
        } catch (Exception e) {
            // No crítico: registrar y continuar
            log.warn("[Saga {}] Notificación falló (no crítico): {}. La saga se considera exitosa.", ctx.getSagaId(), e.getMessage());
        }
    }

    @Override
    public void compensate(SagaContext ctx) {
        // La notificación no tiene compensación real — si ya se envió, se enviaría
        log.info("[Saga {}] Compensación de notificación: no aplica (paso no crítico)", ctx.getSagaId());
    }
}
