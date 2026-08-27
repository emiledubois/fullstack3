package com.smartlogix.pedidos.saga.client;

import com.smartlogix.pedidos.security.InternalTokenSigner;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
@Slf4j
public class EnviosSagaClient {

    private final WebClient webClient;

    public EnviosSagaClient(
            @Value("${envios.service.url}") String url,
            InternalTokenSigner internalTokenSigner) {
        this.webClient = WebClient.builder().baseUrl(url)
            .filter(internalTokenSigner.exchangeFilter()).build();
    }

    @CircuitBreaker(name = "envios-saga", fallbackMethod = "crearFallback")
    public Long crearEnvio(Long pedidoId, String tipoEnvio, String destino, String sagaId) {
        log.info("[Saga {}] Creando envío para pedido={}", sagaId, pedidoId);
        Map<?, ?> resp = webClient.post()
            .uri("/envios")
            .bodyValue(Map.of(
                "pedidoId",   pedidoId,
                "tipoEnvio",  tipoEnvio != null ? tipoEnvio : "TERRESTRE",
                "destino",    destino   != null ? destino   : "Por definir",
                "transportista", "Por asignar"
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        return ((Number) resp.get("id")).longValue();
    }

    @CircuitBreaker(name = "envios-saga", fallbackMethod = "cancelarFallback")
    public void cancelarEnvio(Long envioId, String sagaId) {
        log.info("[Saga {}] Cancelando envío={} (compensación)", sagaId, envioId);
        webClient.delete()
            .uri("/envios/{id}/cancelar?sagaId={sid}", envioId, sagaId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public Long crearFallback(Long pedidoId, String tipo, String destino, String sagaId, Throwable t) {
        throw new RuntimeException("[CB OPEN] No se pudo crear envío: " + t.getMessage());
    }

    public void cancelarFallback(Long envioId, String sagaId, Throwable t) {
        log.error("[Saga {}] ALERTA CRÍTICA: No se pudo cancelar envío={}. Revisión manual.", sagaId, envioId);
    }
}
