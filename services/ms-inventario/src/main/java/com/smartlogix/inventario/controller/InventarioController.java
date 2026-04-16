package com.smartlogix.inventario.controller;

import com.smartlogix.inventario.dto.ProductDTO;
import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final ProductService productService;

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

    // Endpoint para Circuit Breaker de ms-pedidos
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

    // RF-02: alertas de desabastecimiento
    @GetMapping("/alertas")
    public ResponseEntity<List<ProductDTO>> getAlertas() {
        return ResponseEntity.ok(productService.getProductosBajoUmbral());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-inventario UP");
    }
}
