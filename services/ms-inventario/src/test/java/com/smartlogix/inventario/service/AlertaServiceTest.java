package com.smartlogix.inventario.service;

import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.repository.InventarioRepository;
import com.smartlogix.inventario.strategy.AlertaStockStrategy;
import com.smartlogix.inventario.strategy.UmbralFijoStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock private InventarioRepository inventarioRepository;

    // Inyectamos el mapa de estrategias manualmente
    private AlertaService alertaService;

    @BeforeEach
    void setUp() {
        Map<String, AlertaStockStrategy> strategies = Map.of(
            "umbralFijo",  new UmbralFijoStrategy(),
            "porcentaje",  new com.smartlogix.inventario.strategy.PorcentajeStrategy(),
            "critico",     new com.smartlogix.inventario.strategy.StockCriticoStrategy()
        );
        alertaService = new AlertaService(inventarioRepository, strategies);
    }

    @Test
    void umbralFijo_detectaProductoBajoUmbral_retornaSoloEse() {

        // ARRANGE
        // Tres productos: A bajo umbral, B igual al umbral, C sobre umbral
        Producto a = new Producto(); a.setSku("A"); a.setStockActual(3); a.setUmbralMinimo(10);
        Producto b = new Producto(); b.setSku("B"); b.setStockActual(10); b.setUmbralMinimo(10);
        Producto c = new Producto(); c.setSku("C"); c.setStockActual(50); c.setUmbralMinimo(5);

        // Simular que el repositorio retorna los tres productos
        when(inventarioRepository.findAll()).thenReturn(List.of(a, b, c));

        // ACT
        // Obtener alertas con la estrategia por defecto (umbralFijo)
        var respuesta = alertaService.getAlertas();

        // ASSERT
        // Solo el producto A debe estar en alertas
        assertEquals(1, respuesta.getAlertas().size());
        assertEquals("A", respuesta.getAlertas().get(0).getSku());

        // La estrategia reportada debe ser umbral-fijo
        assertEquals("umbral-fijo", respuesta.getEstrategia());
    }

    @Test
    void setStrategy_cambiasACritico_soloDetectaStockCero() {

        // ARRANGE
        // Dos productos: A con stock 2 (bajo umbral pero > 0) y B con stock 0
        Producto a = new Producto(); a.setSku("A"); a.setStockActual(2); a.setUmbralMinimo(5);
        Producto b = new Producto(); b.setSku("B"); b.setStockActual(0); b.setUmbralMinimo(3);
        when(inventarioRepository.findAll()).thenReturn(List.of(a, b));

        // ACT
        // Cambiar estrategia a "critico" en tiempo de ejecución
        alertaService.setStrategy("critico");
        var respuesta = alertaService.getAlertas();

        // ASSERT
        // Solo el producto B (stock 0) debe aparecer
        assertEquals(1, respuesta.getAlertas().size());
        assertEquals("B", respuesta.getAlertas().get(0).getSku());

        // La estrategia reportada es "critico"
        assertEquals("critico", respuesta.getEstrategia());
    }

    @Test
    void setStrategy_nombreInvalido_lanzaExcepcion() {

        // ARRANGE
        // No se necesita preparar repositorio para esta prueba
        // El mapa solo tiene umbralFijo, porcentaje y critico
        String nombreInvalido = "estrategiaInexistente";

        // ACT
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> alertaService.setStrategy(nombreInvalido));

        // ASSERT
        // El mensaje debe mencionar la estrategia solicitada
        assertTrue(ex.getMessage().contains(nombreInvalido));

        // La estrategia activa no debe haber cambiado
        // (verificar que getAlertas no lanza NullPointerException)
        when(inventarioRepository.findAll()).thenReturn(List.of());
        assertDoesNotThrow(() -> alertaService.getAlertas());
    }
}
