package com.smartlogix.inventario.controller;


import com.smartlogix.inventario.security.InternalAuthFilter;
import com.smartlogix.inventario.service.ProductService;
import com.smartlogix.inventario.dto.AlertaResponseDTO;
import com.smartlogix.inventario.service.AlertaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller.
@WebMvcTest(
    value = InventarioController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
@SuppressWarnings("deprecation") // Suprime warnings de @MockBean en versiones < 3.4
class InventarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertaService alertaService;

    @MockBean
    private ProductService productService;

    @Test
    void getAlertasEstrategiaCritico_retorna200ConDTOFiltrado() throws Exception {
        // ARRANGE
        AlertaResponseDTO dtoEsperado = new AlertaResponseDTO();
        dtoEsperado.setEstrategia("critico");
        dtoEsperado.setDescripcionEstrategia("Estrategia crítica: stock mínimo 5 unidades");
        dtoEsperado.setTotalProductos(10);
        dtoEsperado.setAlertas(List.of());

        // Simular que setStrategy no lanza excepción
        doNothing().when(alertaService).setStrategy("critico");

        // Simular que getAlertas devuelve el DTO
        when(alertaService.getAlertas()).thenReturn(dtoEsperado);

        // ACT y ASSERT
        mockMvc.perform(get("/inventario/alertas/estrategia")
                        .param("estrategia", "critico"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.estrategia").value("critico"))
               .andExpect(jsonPath("$.totalProductos").value(10))
               .andExpect(jsonPath("$.alertas").isArray());
    }
}
