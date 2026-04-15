package com.smartlogix.inventario.controller;

import com.smartlogix.inventario.dto.ProductDTO;
import com.smartlogix.inventario.model.Product;
import com.smartlogix.inventario.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class InventarioController {

    private final ProductService productService;

    // Actividad 1.1.2 — IL 1.1: GET /productos
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAll() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // Actividad 1.1.2 — IL 1.1: POST /productos
    @PostMapping
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id,
                                              @Valid @RequestBody Product product) {
        return ResponseEntity.ok(productService.updateProduct(id, product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Health check para Docker
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("product-service UP");
    }

	// Este endpoint es llamado por ms-pedidos con Circuit Breaker para verificar stock
	@GetMapping("/{id}/stock")
	public ResponseEntity<Boolean> verificarStock(
	        @PathVariable Long id,
	        @RequestParam int cantidad) {
	    return productService.getProductById(id)
	        .map(p -> ResponseEntity.ok(p.getStockActual() >= cantidad))
	        .orElse(ResponseEntity.notFound().build());
	}

	// Endpoint de alertas de desabastecimiento (RF-02)
	@GetMapping("/alertas")
	public ResponseEntity<List<ProductDTO>> getProductosBajoStock() {
	    List<ProductDTO> bajos = productService.getProductosBajoUmbral();
	    return ResponseEntity.ok(bajos);
	}



}
