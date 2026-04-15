package com.smartlogix.envios.controller;

import com.smartlogix.envios.dto.*;
import com.smartlogix.envios.service.EnvioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/envios") @RequiredArgsConstructor
public class EnvioController {

    private final EnvioService envioService;

    @PostMapping
    public ResponseEntity<EnvioDTO> crear(@RequestBody CreateEnvioRequest req) {
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
}
