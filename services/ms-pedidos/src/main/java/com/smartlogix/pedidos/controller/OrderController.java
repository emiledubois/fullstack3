package com.smartlogix.pedidos.controller;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.facade.LogisticaFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * CLIENTE DEL PATRÓN FACADE
 *
 * OrderController es el "Cliente" del diagrama del PDF.
 * Del PDF: "El Cliente utiliza la fachada en lugar de invocar
 * directamente los objetos del subsistema."
 *
 * ANTES (sin Facade): el controller conocía OrderService que conocía
 *   InventarioClient + PedidoFactory + OrderRepository + EventPublisher.
 *
 * AHORA (con Facade): el controller solo conoce LogisticaFacade.
 *   Llama a facade.procesarCreacionPedido() — UNA SOLA LLAMADA.
 *   No sabe nada de Circuit Breaker, Factory Method, ni Observer.
 *
 * Del PDF (analogía): el cliente llama al operador telefónico (fachada).
 *   El operador coordina Almacén, Empaquetado y Entrega internamente.
 */
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    // El controller solo conoce la FACHADA — no los subsistemas
    private final LogisticaFacade logisticaFacade;

    /**
     * Crear pedido — una sola llamada a la fachada.
     * La fachada coordina los 4 subsistemas internamente.
     */
    @PostMapping
    public ResponseEntity<OrderDTO> create(@RequestBody CreatePedidoRequest req) {
        // Del PDF: "el cliente tan solo incluye las funciones realmente
        // importantes para los clientes" — aquí es solo procesarCreacionPedido().
        OrderDTO resultado = logisticaFacade.procesarCreacionPedido(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAll() {
        return ResponseEntity.ok(logisticaFacade.listarPedidos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(logisticaFacade.obtenerPedido(id));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-pedidos UP (Facade activo)");
    }
}
