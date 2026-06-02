package com.smartlogix.pagos.controller;

import com.smartlogix.pagos.dto.*;
import com.smartlogix.pagos.service.PagoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
@Slf4j
public class PagoController {

    private final PagoService pagoService;

    /**
     * POST /pagos/crear
     * Llamado por ms-pedidos al crear un pedido con metodoPago=FLOW.
     * Retorna la URL de pago para que el frontend redirija al usuario.
     */
    @PostMapping("/crear")
    public ResponseEntity<CrearPagoResponse> crearPago(
            @RequestBody CrearPagoRequest req) {
        return ResponseEntity.ok(pagoService.iniciarPago(req));
    }

    /**
     * POST /pagos/webhook/flow
     * Endpoint que Flow llama como urlConfirmation.
     * IMPORTANTE: este endpoint DEBE ser público (sin JWT).
     * Se excluye del AuthFilter en el API Gateway.
     *
     * Fuente: developers.flow.cl — Confirmación de orden
     * Flow envía el token como parámetro POST form-encoded.
     */
    @PostMapping("/webhook/flow")
    public ResponseEntity<String> webhookFlow(
            @RequestParam("token") String token,
            @RequestParam(value = "s", required = false) String firma) {
        log.info("[Webhook Flow] token={}", token);
        try {
            pagoService.procesarWebhook(token, firma);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("[Webhook Flow] Error: {}", e.getMessage());
            // Retornar 200 de todas formas para que Flow no reintente
            return ResponseEntity.ok("ERROR_INTERNO");
        }
    }

    /** GET /pagos/{id} — consultar estado de un pago */
    @GetMapping("/{id}")
    public ResponseEntity<?> consultarPago(@PathVariable Long id) {
        return pagoService.buscarPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
