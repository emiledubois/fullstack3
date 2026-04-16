package com.smartlogix.inventario.service;

import com.smartlogix.inventario.dto.ProductDTO;
import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.repository.InventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final InventarioRepository inventarioRepository;

    public List<ProductDTO> getAllProducts() {
        return inventarioRepository.findAll()
                .stream().map(ProductDTO::from).collect(Collectors.toList());
    }

    public ProductDTO getProductById(Long id) {
        return inventarioRepository.findById(id)
                .map(ProductDTO::from)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
    }

    public ProductDTO createProduct(Producto producto) {
        return ProductDTO.from(inventarioRepository.save(producto));
    }

    public ProductDTO updateProduct(Long id, Producto updated) {
        Producto existing = inventarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));
        existing.setNombre(updated.getNombre());
        existing.setPrecioUnitario(updated.getPrecioUnitario());
        existing.setStockActual(updated.getStockActual());
        existing.setUmbralMinimo(updated.getUmbralMinimo());
        existing.setBodega(updated.getBodega());
        existing.setDescripcion(updated.getDescripcion());
        return ProductDTO.from(inventarioRepository.save(existing));
    }

    public void deleteProduct(Long id) {
        if (!inventarioRepository.existsById(id))
            throw new RuntimeException("Producto no encontrado: " + id);
        inventarioRepository.deleteById(id);
    }

    // RF-02: alertas de desabastecimiento
    public List<ProductDTO> getProductosBajoUmbral() {
        return inventarioRepository.findAll().stream()
                .filter(p -> p.getStockActual() < p.getUmbralMinimo())
                .map(ProductDTO::from)
                .collect(Collectors.toList());
    }
}
