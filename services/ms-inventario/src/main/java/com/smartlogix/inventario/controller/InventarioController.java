package com.smartlogix.inventario.controller;

import com.smartlogix.inventario.dto.ProductDTO;
import com.smartlogix.inventario.dto.AlertaResponseDTO;
import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.service.ProductService;
import com.smartlogix.inventario.service.AlertaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventario")
@RequiredArgsConstructor
@Slf4j
public class InventarioController {

    private final ProductService productService;
    private final AlertaService alertaService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAll() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody Producto producto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(producto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id,
                                              @Valid @RequestBody Producto producto) {
        return ResponseEntity.ok(productService.updateProduct(id, producto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<Boolean> verificarStock(@PathVariable Long id,
                                                   @RequestParam int cantidad) {
        try {
            ProductDTO p = productService.getProductById(id);
            return ResponseEntity.ok(p.getStockActual() >= cantidad);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endpoint reemplazado: ahora retorna AlertaResponseDTO (Strategy)
    @GetMapping("/alertas")
    public ResponseEntity<AlertaResponseDTO> getAlertas() {
        return ResponseEntity.ok(alertaService.getAlertas());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-inventario UP");
    }

    @PostMapping("/{id}/reservar")
    public ResponseEntity<String> reservarStock(
            @PathVariable Long id,
            @RequestParam int cantidad,
            @RequestParam(required = false) String sagaId) {

        ProductDTO producto = productService.getProductById(id);

        if (producto.getStockActual() < cantidad) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Stock insuficiente: disponible=" + producto.getStockActual()
                            + ", requerido=" + cantidad);
        }

        productService.decrementarStock(id, cantidad);
        log.info("[Inventario] Stock reservado: productoId={}, cantidad={}, sagaId={}", id, cantidad, sagaId);
        return ResponseEntity.ok("Stock reservado");
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<String> liberarStock(
            @PathVariable Long id,
            @RequestParam int cantidad,
            @RequestParam(required = false) String sagaId) {

        productService.incrementarStock(id, cantidad);
        log.info("[Inventario] Stock liberado (compensación): productoId={}, cantidad={}, sagaId={}", id, cantidad, sagaId);
        return ResponseEntity.ok("Stock liberado");
    }

    @GetMapping("/alertas/estrategia")
    public ResponseEntity<AlertaResponseDTO> getAlertasConEstrategia(
            @RequestParam(defaultValue = "umbralFijo") String estrategia) {
        alertaService.setStrategy(estrategia);
        return ResponseEntity.ok(alertaService.getAlertas());
    }

    @GetMapping("/alertas/estrategias")
    public ResponseEntity<List<String>> getEstrategias() {
        return ResponseEntity.ok(alertaService.getEstrategiasDisponibles());
    }
}
