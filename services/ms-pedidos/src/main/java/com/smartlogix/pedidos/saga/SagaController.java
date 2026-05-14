package com.smartlogix.pedidos.saga;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sagas")
@RequiredArgsConstructor
@Slf4j
public class SagaController {

    private final SagaOrchestrator orchestrator;

    /**
     * Inicia la Saga completa de creación de pedido.
     * Coordina: reservar stock → crear pedido → crear envío → notificar.
     * Si cualquier paso falla, se ejecutan compensaciones en orden inverso.
     */
    @PostMapping("/pedido")
    public ResponseEntity<?> crearPedidoSaga(@RequestBody CreatePedidoRequest req) {
        try {
            SagaResultado resultado = orchestrator.ejecutar(req);
            if (resultado.isExitoso()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sagaId",   resultado.getSagaId(),
                    "pedidoId", resultado.getPedidoId(),
                    "envioId",  resultado.getEnvioId(),
                    "status",   "COMPLETADA"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "sagaId", resultado.getSagaId(),
                    "status", "FALLIDA",
                    "error",  resultado.getError()
                ));
            }
        } catch (Exception e) {
            log.error("Error inesperado iniciando saga: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno al procesar la saga"));
        }
    }

    /**
     * Consultar el estado persistido de una saga.
     * Útil para verificar compensaciones y auditoría.
     */
    @GetMapping("/{sagaId}")
    public ResponseEntity<?> consultarSaga(@PathVariable String sagaId) {
        return orchestrator.consultarEstado(UUID.fromString(sagaId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
