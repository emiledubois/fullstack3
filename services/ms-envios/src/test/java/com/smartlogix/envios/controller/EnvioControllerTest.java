package com.smartlogix.envios.controller;

import com.smartlogix.envios.dto.EnvioDatosDTO;
import com.smartlogix.envios.security.InternalAuthFilter;
import com.smartlogix.envios.service.EnvioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller.
@WebMvcTest(
    value = EnvioController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
class EnvioControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private EnvioService envioService;

    @Test
    void getPorPedidos_conPedidoIdsValidos_retorna200ConLista() throws Exception {

        // ARRANGE
        EnvioDatosDTO dto = EnvioDatosDTO.builder()
                .id(17L).pedidoId(42L).status("ENTREGADO")
                .tipoEnvio("TERRESTRE").transportista("Transportes del Sur")
                .destino("Valparaíso").build();
        when(envioService.getPorPedidoIds(eq(List.of(1L, 2L, 3L)))).thenReturn(List.of(dto));

        // ACT
        var resultado = mockMvc.perform(get("/envios/interno/por-pedidos").param("pedidoIds", "1,2,3"));

        // ASSERT
        resultado.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(17))
                .andExpect(jsonPath("$[0].pedidoId").value(42));
    }

    @Test
    void getPorPedidos_sinParametro_retorna200ConListaVaciaSinLlamarAlServicio() throws Exception {

        // ARRANGE — ningún pedido → api-gateway debería omitir la llamada,
        // pero si llega igual, debe responder [] sin tocar el repositorio

        // ACT
        var resultado = mockMvc.perform(get("/envios/interno/por-pedidos"));

        // ASSERT
        resultado.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        verify(envioService, org.mockito.Mockito.never()).getPorPedidoIds(any());
    }

    @Test
    void getPorPedidos_valorNoNumerico_retorna400() throws Exception {

        // ARRANGE — defensive input validation (§3.4 del diseño)

        // ACT
        var resultado = mockMvc.perform(get("/envios/interno/por-pedidos").param("pedidoIds", "1,abc,3"));

        // ASSERT
        resultado.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
