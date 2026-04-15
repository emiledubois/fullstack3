package com.smartlogix.inventario.repository;

import com.smartlogix.inventario.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findBySku(String sku);
    List<Producto> findByStockActualLessThan(Integer umbral);  // Para alertas
    List<Producto> findByBodega(String bodega);
}
