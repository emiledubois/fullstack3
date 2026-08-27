package com.smartlogix.envios.controller;

import com.smartlogix.envios.dto.*;
import com.smartlogix.envios.service.EnvioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/envios") @RequiredArgsConstructor
public class EnvioController {

    private final EnvioService envioService;

    @PostMapping
    public ResponseEntity<EnvioDTO> crear(@Valid @RequestBody CreateEnvioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(envioService.crearEnvio(req));
    }

    @GetMapping
    public ResponseEntity<List<EnvioDTO>> getAll() {
        return ResponseEntity.ok(envioService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvioDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(envioService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EnvioDTO> updateStatus(@PathVariable Long id,
                                                  @RequestParam String status) {
        return ResponseEntity.ok(envioService.actualizarStatus(id, status));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-envios UP");
    }

    /**
     * No enrutable vía /api/envios/** (excluido en GatewayConfig.enviosRoute(),
     * ver arco-acceso-personal-data.md §3.4/§5). Solo alcanzable por
     * api-gateway — InternalAuthFilter ya lo permite en su bucket por
     * defecto, sin cambios de código.
     */
    @GetMapping("/interno/por-pedidos")
    public ResponseEntity<?> getPorPedidos(
            @RequestParam(required = false) String pedidoIds) {
        if (pedidoIds == null || pedidoIds.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<Long> ids = new ArrayList<>();
        for (String token : pedidoIds.split(",")) {
            try {
                ids.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "pedidoIds contiene un valor no numérico: " + token));
            }
        }
        return ResponseEntity.ok(envioService.getPorPedidoIds(ids));
    }

@DeleteMapping("/{id}/cancelar")
public ResponseEntity<String> cancelarEnvio(
        @PathVariable Long id,
        @RequestParam(required = false) String sagaId) {
    envioService.actualizarStatus(id, "CANCELADO");
    return ResponseEntity.ok("Envío " + id + " cancelado (compensación saga=" + sagaId + ")");
}

}
