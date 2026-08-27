package com.smartlogix.pedidos.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.security.InternalAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller/orquestador.
@WebMvcTest(
    value = SagaController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
class SagaControllerTest {

    @Autowired private MockMvc      mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private SagaOrchestrator orchestrator;

    @Test
    void postSagasPedido_sagaExitosa_retorna201ConIds() throws Exception {

        // ARRANGE
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("test@t.cl");
        req.setClienteNombre("Test Cliente");
        req.setTipoPedido("NACIONAL");
        req.setDestino("Santiago");
        req.setTotal(300.0);
        req.setProductoId(1L);
        req.setCantidad(2);

        UUID sagaId = UUID.randomUUID();
        SagaResultado resultado = SagaResultado.exito(sagaId, 10L, 20L);
        when(orchestrator.ejecutar(any())).thenReturn(resultado);

        // ACT
        var respuesta = mockMvc.perform(
            post("/sagas/pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        );

        // ASSERT
        respuesta
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETADA"))
            .andExpect(jsonPath("$.pedidoId").value(10))
            .andExpect(jsonPath("$.envioId").value(20));
    }

    @Test
    void postSagasPedido_sagaFalla_retorna409ConError() throws Exception {

        // ARRANGE
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("test@t.cl");
        req.setClienteNombre("Test Cliente");
        req.setTipoPedido("NACIONAL");
        req.setDestino("Santiago");
        req.setTotal(300.0);
        req.setProductoId(1L);
        req.setCantidad(1);

        UUID sagaId = UUID.randomUUID();
        SagaResultado fallido = SagaResultado.fallo(sagaId, "ms-envios no responde");
        when(orchestrator.ejecutar(any())).thenReturn(fallido);

        // ACT
        var respuesta = mockMvc.perform(
            post("/sagas/pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        );

        // ASSERT
        respuesta
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value("FALLIDA"))
            .andExpect(jsonPath("$.error").exists());
    }
}
