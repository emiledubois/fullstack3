package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WebHub / Backend for Frontend (BFF)
 *
 * Endpoint: GET /api/dashboard
 *
 * Agrega en paralelo datos de:
 *   - ms-inventario  (productos, alertas de stock)
 *   - ms-pedidos     (últimos pedidos)
 *   - ms-envios      (últimos envíos)
 *
 * Devuelve un DashboardDTO único con métricas calculadas.
 * El frontend hace UNA petición en vez de cuatro.
 */
@RestController
@RequestMapping("/dashboard")
@Slf4j
public class DashboardController {

    private final WebClient inventarioClient;
    private final WebClient pedidosClient;
    private final WebClient enviosClient;

    public DashboardController(
            @Value("${inventario.service.url:http://ms-inventario:8082}") String invUrl,
            @Value("${pedidos.service.url:http://ms-pedidos:8083}")      String pedUrl,
            @Value("${envios.service.url:http://ms-envios:8084}")        String envUrl,
            WebClient.Builder builder) {
        this.inventarioClient = builder.baseUrl(invUrl).build();
        this.pedidosClient    = builder.baseUrl(pedUrl).build();
        this.enviosClient     = builder.baseUrl(envUrl).build();
    }

    /**
     * GET /api/dashboard
     *
     * El AuthFilter del gateway ya validó el JWT (ahora vía cookie httpOnly)
     * antes de llegar aquí. ms-inventario/ms-pedidos/ms-envios nunca
     * implementaron su propia autenticación (no leen Authorization ni
     * X-User-Email) — no hay nada que propagar en estas llamadas internas.
     */
    @GetMapping
    public DashboardDTO getDashboard() {
        log.info("[WebHub] GET /api/dashboard — aggregating 3 services in parallel");

        // Llamadas paralelas con Mono.zip
        // Las tres llamadas se inician SIMULTÁNEAMENTE.
        // El tiempo total es el del servicio más lento, no la suma.
        Mono<List<ProductoResumen>> productosMono = inventarioClient
            .get().uri("/inventario")
            .retrieve()
            .bodyToFlux(ProductoResumen.class)
            .collectList()
            // onErrorReturn: si ms-inventario está caído, devolver lista vacía
            // El dashboard igual carga — tolerancia a fallos parciales
            .onErrorReturn(Collections.emptyList());

        Mono<List<PedidoResumen>> pedidosMono = pedidosClient
            .get().uri("/pedidos")
            .retrieve()
            .bodyToFlux(PedidoResumen.class)
            .collectList()
            .onErrorReturn(Collections.emptyList());

        Mono<List<EnvioResumen>> enviosMono = enviosClient
            .get().uri("/envios")
            .retrieve()
            .bodyToFlux(EnvioResumen.class)
            .collectList()
            .onErrorReturn(Collections.emptyList());

        // Zip: espera que los tres Mono completen y combina los resultados
        return Mono.zip(productosMono, pedidosMono, enviosMono)
            .map(tuple -> buildDashboard(
                tuple.getT1(),  // productos
                tuple.getT2(),  // pedidos
                tuple.getT3()   // envíos
            ))
            .block(); // bloquear: el controller MVC necesita un valor síncrono
    }

    // Construcción del DashboardDTO con métricas
    private DashboardDTO buildDashboard(
            List<ProductoResumen> productos,
            List<PedidoResumen>   pedidos,
            List<EnvioResumen>    envios) {

        // Métricas calculadas en el BFF — el frontend no hace ningún cálculo
        List<ProductoResumen> alertas = productos.stream()
            .filter(p -> p.getStockActual() != null && p.getUmbralMinimo() != null
                      && p.getStockActual() < p.getUmbralMinimo())
            .collect(Collectors.toList());

        double valorInventario = productos.stream()
            .filter(p -> p.getPrecioUnitario() != null && p.getStockActual() != null)
            .mapToDouble(p -> p.getPrecioUnitario() * p.getStockActual())
            .sum();

        MetricasDTO metricas = MetricasDTO.builder()
            .totalProductos(productos.size())
            .productosConAlertaStock(alertas.size())
            .totalPedidos(pedidos.size())
            .pedidosPendientes((int) pedidos.stream()
                .filter(p -> "PENDIENTE".equals(p.getStatus())).count())
            .pedidosConfirmados((int) pedidos.stream()
                .filter(p -> "CONFIRMADO".equals(p.getStatus())).count())
            .totalEnvios(envios.size())
            .enviosEnRuta((int) envios.stream()
                .filter(e -> "EN_RUTA".equals(e.getStatus())).count())
            .valorTotalInventario(Math.round(valorInventario * 100.0) / 100.0)
            .build();

        // Determinar estado general según si hay datos o listas vacías
        String estado = determinarEstado(productos, pedidos, envios);

        return DashboardDTO.builder()
            .metricas(metricas)
            .productos(productos)
            .alertasStock(alertas)
            .ultimosPedidos(pedidos.stream()
                .sorted(Comparator.comparing(PedidoResumen::getId,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList()))
            .ultimosEnvios(envios.stream()
                .sorted(Comparator.comparing(EnvioResumen::getId,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .collect(Collectors.toList()))
            .generadoEn(LocalDateTime.now())
            .estadoServicios(estado)
            .build();
    }

    private String determinarEstado(
            List<ProductoResumen> p, List<PedidoResumen> ped, List<EnvioResumen> e) {
        // Si todos son vacíos puede haber un problema general
        if (p.isEmpty() && ped.isEmpty() && e.isEmpty()) return "ERROR";
        // Si alguno es vacío pero no todos, hay fallo parcial
        if (p.isEmpty() || ped.isEmpty() || e.isEmpty()) return "PARCIAL";
        return "OK";
    }
}
