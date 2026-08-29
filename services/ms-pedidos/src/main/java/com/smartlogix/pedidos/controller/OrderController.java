package com.smartlogix.pedidos.controller;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.dto.PedidoDatosDTO;
import com.smartlogix.pedidos.facade.LogisticaFacade;
import com.smartlogix.pedidos.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    // El controller solo conoce la FACHADA — no los subsistemas
    private final LogisticaFacade logisticaFacade;
    private final OrderService orderService;

    /**
     * Crear pedido — una sola llamada a la fachada.
     * La fachada coordina los 4 subsistemas internamente.
     */
    @PostMapping
    public ResponseEntity<OrderDTO> create(@Valid @RequestBody CreatePedidoRequest req) {
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

    /**
     * No enrutable vía /api/pedidos/** (excluido en GatewayConfig.pedidosRoute(),
     * ver arco-acceso-personal-data.md §3.3/§5). Solo alcanzable por
     * api-gateway — InternalAuthFilter ya lo permite en su bucket por
     * defecto ("todo lo demás → solo api-gateway"), sin cambios de código.
     */
    @GetMapping("/interno/por-email/{email}")
    public ResponseEntity<List<PedidoDatosDTO>> getPedidosPorEmail(@PathVariable String email) {
        return ResponseEntity.ok(orderService.getPedidosByUserEmail(email));
    }

    /**
     * Derecho de cancelación ARCO+ (arco-cancelacion-oposicion.md §4.1/§6.3).
     * No enrutable vía /api/pedidos/** (excluido en GatewayConfig.pedidosRoute(),
     * mismo patrón que getPedidosPorEmail). Solo alcanzable por api-gateway —
     * InternalAuthFilter ya lo permite en su bucket por defecto, sin cambios
     * de código.
     */
    @PutMapping("/interno/por-email/{email}/anonimizar")
    public ResponseEntity<Map<String, Integer>> anonimizarPorEmail(@PathVariable String email) {
        int cantidad = orderService.anonimizarPorEmail(email);
        return ResponseEntity.ok(Map.of("cantidadAnonimizada", cantidad));
    }

    /**
     * Llamado internamente por ms-pagos cuando Flow confirma el pago.
     * Cambia el estado del pedido a PAGADO y dispara la Saga.
     * No pasa por el JWT del gateway — es comunicación interna.
     */
    @PostMapping("/{id}/confirmar-pago")
    public ResponseEntity<String> confirmarPago(
            @PathVariable Long id,
            @RequestParam String token) {
        orderService.confirmarPagoYDispararSaga(id, token);
        return ResponseEntity.ok("Saga disparada para pedido " + id);
    }

    /**
     * Llamado si el pago fue rechazado o anulado por Flow
     */
    @PostMapping("/{id}/pago-fallido")
    public ResponseEntity<String> pagoFallido(
            @PathVariable Long id,
            @RequestParam String estado) {
        orderService.marcarPagoFallido(id, estado);
        return ResponseEntity.ok("Pedido " + id + " actualizado a " + estado);
    }



    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)  // 409
            .body(Map.of("error", ex.getMessage()));
    }

}
