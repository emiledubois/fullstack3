package com.smartlogix.pedidos.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

// Circuit Breaker 
// Llama a ms-inventario. Si falla, activa stockFallback.
@Component
@Slf4j
public class InventarioClient {

    private final WebClient webClient;

    public InventarioClient(@Value("${inventario.service.url:http://ms-inventario:8082}") String url) {
        this.webClient = WebClient.builder().baseUrl(url).build();
    }

    @CircuitBreaker(name = "inventario", fallbackMethod = "stockFallback")
    public Boolean verificarStock(Long productoId, int cantidad) {
        log.info("Verificando stock: productoId={}, cantidad={}", productoId, cantidad);
        return webClient.get()
            .uri("/inventario/{id}/stock?cantidad={qty}", productoId, cantidad)
            .retrieve()
            .bodyToMono(Boolean.class)
            .block();
    }

    // Fallback: se ejecuta cuando el circuito está OPEN o ms-inventario falla
    public Boolean stockFallback(Long productoId, int cantidad, Throwable t) {
        log.warn("Circuit Breaker OPEN para inventario. Fallback activado. Error: {}", t.getMessage());
        // En SmartLogix: rechazamos el pedido para no vender sin stock confirmado
        return false;
    }
}
