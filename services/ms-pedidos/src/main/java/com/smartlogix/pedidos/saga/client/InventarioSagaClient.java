package com.smartlogix.pedidos.saga.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
@Slf4j
public class InventarioSagaClient {

    private final WebClient webClient;

    public InventarioSagaClient(@Value("${inventario.service.url}") String url) {
        this.webClient = WebClient.builder().baseUrl(url).build();
    }

    @CircuitBreaker(name = "inventario-saga", fallbackMethod = "reservarFallback")
    public void reservarStock(Long productoId, int cantidad, String sagaId) {
        log.info("[Saga {}] Reservando stock: productoId={}, cantidad={}", sagaId, productoId, cantidad);
        webClient.post()
            .uri("/inventario/{id}/reservar?cantidad={qty}&sagaId={sid}",
                 productoId, cantidad, sagaId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Error reservando stock: " + body))
            )
            .toBodilessEntity()
            .block();
    }

    @CircuitBreaker(name = "inventario-saga", fallbackMethod = "liberarFallback")
    public void liberarStock(Long productoId, int cantidad, String sagaId) {
        log.info("[Saga {}] Liberando stock (compensación): productoId={}, cantidad={}", sagaId, productoId, cantidad);
        webClient.post()
            .uri("/inventario/{id}/liberar?cantidad={qty}&sagaId={sid}",
                 productoId, cantidad, sagaId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Error liberando stock: " + body))
            )
            .toBodilessEntity()
            .block();
    }

    public void reservarFallback(Long productoId, int cantidad, String sagaId, Throwable t) {
        throw new RuntimeException("[CB OPEN] No se pudo reservar stock para saga " + sagaId + ": " + t.getMessage());
    }

    public void liberarFallback(Long productoId, int cantidad, String sagaId, Throwable t) {
        // La compensación falla silenciosamente — se registra para revisión manual
        log.error("[Saga {}] ALERTA: No se pudo liberar stock (Circuit Breaker abierto). Intervención manual requerida.", sagaId);
    }
}
