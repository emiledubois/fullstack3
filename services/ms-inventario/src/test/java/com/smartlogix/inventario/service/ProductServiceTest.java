package com.smartlogix.inventario.service;

import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.repository.InventarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private InventarioRepository inventarioRepository;
    @InjectMocks private ProductService service;

    @Test @DisplayName("getAllProducts retorna lista de DTOs")
    void getAllProducts_returnsAll() {
        when(inventarioRepository.findAll()).thenReturn(List.of(
            Producto.builder().id(1L).sku("SKU001").nombre("Laptop")
                .precioUnitario(999.0).stockActual(50).umbralMinimo(5).build()
        ));
        assertThat(service.getAllProducts()).hasSize(1);
        assertThat(service.getAllProducts().get(0).getNombre()).isEqualTo("Laptop");
    }

    @Test @DisplayName("getProductById lanza excepción si no existe")
    void getById_throwsWhenNotFound() {
        when(inventarioRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProductById(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Producto no encontrado");
    }

    @Test @DisplayName("tieneStock detecta stock insuficiente")
    void tieneStock_returnsFalseWhenLow() {
        Producto p = Producto.builder().stockActual(3).umbralMinimo(10).build();
        assertThat(p.tieneStock(10)).isFalse();
    }

    @Test @DisplayName("getProductosBajoUmbral filtra correctamente")
    void getBajoUmbral_filtersCorrectly() {
        when(inventarioRepository.findAll()).thenReturn(List.of(
            Producto.builder().id(1L).stockActual(3).umbralMinimo(10).build(),
            Producto.builder().id(2L).stockActual(50).umbralMinimo(5).build()
        ));
        assertThat(service.getProductosBajoUmbral()).hasSize(1);
    }
}
