package com.smartlogix.inventario.controller;

import com.smartlogix.inventario.dto.AlertaResponseDTO;
import com.smartlogix.inventario.service.AlertaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventarioController.class)
class InventarioControllerTest {

    @Autowired private MockMvc      mockMvc;
    @MockBean  private AlertaService alertaService;
    // Agregar otros @MockBean que necesite el controller

    @Test
    void getAlertasEstrategiaCritico_retorna200ConDTOFiltrado() throws Exception {

        // ARRANGE 
        // Preparar el DTO de respuesta que simulará el servicio
        AlertaResponseDTO dto = new AlertaResponseDTO();
        dto.setEstrategia("critico");
        dto.setTotalProductos(10);
        dto.setAlertas(List.of()); // solo un producto con stock 0

        // Simular que AlertaService retorna el DTO con estrategia critico
        when(alertaService.getAlertasConEstrategia("critico")).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(
            get("/inventario/alertas/estrategia")
                .param("estrategia", "critico")
        );

        // ASSERT
        respuesta
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estrategia").value("critico"))
            .andExpect(jsonPath("$.totalProductos").value(10));
    }
}
