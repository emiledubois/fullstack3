package com.smartlogix.pedidos.saga;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sagas")
@RequiredArgsConstructor
@Slf4j
public class SagaController {

    private final SagaOrchestrator     orchestrator;
    private final SagaEstadoRepository sagaRepo;   // Inyectado directamente

    /**
     * POST /sagas/pedido
     * Inicia la saga de creación de pedido.
     * El frontend llamará a este endpoint en lugar de /pedidos.
     */
    @PostMapping("/pedido")
    public ResponseEntity<?> crearPedidoSaga(@RequestBody CreatePedidoRequest req) {
        try {
            SagaResultado resultado = orchestrator.ejecutar(req);

            if (resultado.isExitoso()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sagaId",   resultado.getSagaId().toString(),
                    "pedidoId", resultado.getPedidoId(),
                    "envioId",  resultado.getEnvioId(),
                    "status",   "COMPLETADA"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "sagaId", resultado.getSagaId().toString(),
                    "status", "FALLIDA",
                    "error",  resultado.getError()
                ));
            }
        } catch (Exception e) {
            log.error("Error inesperado iniciando saga: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error interno al procesar la saga"
            ));
        }
    }

    /**
     * GET /sagas/{sagaId}
     * Consultar el estado de una saga específica (útil para debugging, opcional).
     * El frontend no necesita esto obligatoriamente.
     */
    @GetMapping("/{sagaId}")
    public ResponseEntity<?> consultarSaga(@PathVariable String sagaId) {
        return sagaRepo.findById(UUID.fromString(sagaId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
