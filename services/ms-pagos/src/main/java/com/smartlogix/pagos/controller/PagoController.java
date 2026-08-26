package com.smartlogix.pagos.controller;

import com.smartlogix.pagos.dto.CrearPagoRequest;
import com.smartlogix.pagos.dto.CrearPagoResponse;
import com.smartlogix.pagos.service.PagoService;
import jakarta.validation.Valid;
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

    @PostMapping("/crear")
    public ResponseEntity<CrearPagoResponse> crearPago(@Valid @RequestBody CrearPagoRequest req) {
        return ResponseEntity.ok(pagoService.iniciarPago(req));
    }

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
            return ResponseEntity.ok("ERROR_INTERNO");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> consultarPago(@PathVariable Long id) {
        return pagoService.buscarPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/por-token/{token}")
    public ResponseEntity<?> buscarPorToken(@PathVariable String token) {
        return pagoService.buscarPorToken(token)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
