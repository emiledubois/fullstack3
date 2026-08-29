package com.smartlogix.pagos.controller;

import com.smartlogix.pagos.security.InternalAuthFilter;
import com.smartlogix.pagos.service.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller (mismo patrón que
// OrderControllerTest/UsuarioInternoControllerTest).
@WebMvcTest(
    value = PagoController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
class PagoControllerTest {

    @Autowired private MockMvc      mockMvc;
    @MockBean  private PagoService  pagoService;

    @Test
    void webhookFlow_errorInterno_retorna200ParaEvitarReintentos() throws Exception {

        // ARRANGE
        // Simular que PagoService lanza excepción al procesar el webhook
        // (caso donde el token no existe en la BD)
        doThrow(new RuntimeException("Token no encontrado: TOKEN_ERR"))
            .when(pagoService).procesarWebhook(any(), any());

        // ACT
        // Simular que Flow envía el token como parámetro form-encoded
        var respuesta = mockMvc.perform(
            post("/pagos/webhook/flow")
                .param("token", "TOKEN_ERR")
        );

        // ASSERT
        // SIEMPRE debe retornar 200 para que Flow no reintente
        respuesta
            .andExpect(status().isOk())
            .andExpect(content().string("ERROR_INTERNO"));
    }

    @Test
    void anonimizarPorEmail_cuentaConPagos_retorna200ConCantidadAnonimizada() throws Exception {

        // ARRANGE — derecho de cancelación ARCO+ (arco-cancelacion-oposicion.md §6.4)
        when(pagoService.anonimizarPorEmail("dueña@pyme.cl")).thenReturn(1);

        // ACT
        var respuesta = mockMvc.perform(put("/pagos/interno/por-email/dueña@pyme.cl/anonimizar"));

        // ASSERT
        respuesta.andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidadAnonimizada").value(1));
    }

    @Test
    void anonimizarPorEmail_cuentaSinPagos_retorna200ConCantidadCero() throws Exception {

        // ARRANGE — 0 es un resultado legítimo, no un error
        when(pagoService.anonimizarPorEmail("nadie@pyme.cl")).thenReturn(0);

        // ACT
        var respuesta = mockMvc.perform(put("/pagos/interno/por-email/nadie@pyme.cl/anonimizar"));

        // ASSERT
        respuesta.andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidadAnonimizada").value(0));
    }
}
