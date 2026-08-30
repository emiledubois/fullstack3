package com.smartlogix.pedidos.saga.client;

import com.smartlogix.pedidos.security.InternalTokenSigner;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class InventarioSagaClient {

    private final WebClient webClient;

    public InventarioSagaClient(
            @Value("${inventario.service.url}") String url,
            InternalTokenSigner internalTokenSigner) {
        this.webClient = WebClient.builder().baseUrl(url)
            .filter(internalTokenSigner.exchangeFilter()).build();
    }

    @CircuitBreaker(name = "inventario-saga", fallbackMethod = "reservarFallback")
    public void reservarStock(Long productoId, int cantidad, String sagaId) {
        log.info("[Saga {}] Reservando stock: productoId={}, cantidad={}", sagaId, productoId, cantidad);
        String correlationId = MDC.get("correlationId");
        webClient.post()
            .uri("/inventario/{id}/reservar?cantidad={qty}&sagaId={sid}",
                 productoId, cantidad, sagaId)
            .header("X-Correlation-Id", correlationId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Error reservando stock: " + body)))
            .toBodilessEntity()
            .block();
    }

    @CircuitBreaker(name = "inventario-saga", fallbackMethod = "liberarFallback")
    public void liberarStock(Long productoId, int cantidad, String sagaId) {
        log.info("[Saga {}] Liberando stock (compensación): productoId={}", sagaId, productoId);
        String correlationId = MDC.get("correlationId");
        webClient.post()
            .uri("/inventario/{id}/liberar?cantidad={qty}&sagaId={sid}",
                 productoId, cantidad, sagaId)
            .header("X-Correlation-Id", correlationId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Error liberando stock: " + body)))
            .toBodilessEntity()
            .block();
    }

    // Fallback del Circuit Breaker — lanza excepción para que el orquestador compense
    public void reservarFallback(Long productoId, int cantidad, String sagaId, Throwable t) {
        throw new RuntimeException("[CB OPEN] No se pudo reservar stock: " + t.getMessage());
    }

    // Fallback de compensación — log de alerta para revisión manual
    public void liberarFallback(Long productoId, int cantidad, String sagaId, Throwable t) {
        log.error("[Saga {}] ALERTA CRÍTICA: No se pudo liberar stock productoId={}. Requiere intervención manual.", sagaId, productoId);
    }
}
