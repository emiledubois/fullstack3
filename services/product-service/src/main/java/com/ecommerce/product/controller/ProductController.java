package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductDTO;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductController {

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

    @GetMapping("/{id}/price")
    public ResponseEntity<Double> getPriceWithDiscount(
        @PathVariable Long id,
        @RequestParam(defaultValue = "DEFAULT") String userType) {
        return ResponseEntity.ok(productService.calculateDiscountedPrice(id, userType));
    }
// Prueba: GET /productos/1/price?userType=VIP


}
