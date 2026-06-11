package com.smartlogix.pagos.controller;

import com.smartlogix.pagos.service.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = PagoController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    }
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
}
