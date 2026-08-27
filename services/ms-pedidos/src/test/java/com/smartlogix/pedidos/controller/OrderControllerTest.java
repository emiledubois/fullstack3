package com.smartlogix.pedidos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.dto.PedidoDatosDTO;
import com.smartlogix.pedidos.facade.LogisticaFacade;
import com.smartlogix.pedidos.security.InternalAuthFilter;
import com.smartlogix.pedidos.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller/fachada.
@WebMvcTest(
    value = OrderController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
class OrderControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;
    @MockBean  private LogisticaFacade logisticaFacade;
    @MockBean  private OrderService  orderService;

    @Test
    void postPedidos_requestValido_retorna201ConOrderDTO() throws Exception {

        // ARRANGE
        // Preparar el request y el DTO de respuesta esperado
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("test@t.cl");
        req.setClienteNombre("Test Cliente");
        req.setTotal(999.0);
        req.setTipoPedido("NACIONAL");
        req.setDestino("Santiago");
        req.setProductoId(1L);
        req.setCantidad(1);

        OrderDTO dtoRespuesta = new OrderDTO();
        dtoRespuesta.setId(1L);
        dtoRespuesta.setStatus("PENDIENTE");
        dtoRespuesta.setTipoPedido("NACIONAL");

        // Simular que la fachada procesa el pedido correctamente
        when(logisticaFacade.procesarCreacionPedido(any())).thenReturn(dtoRespuesta);

        // ACT
        // Realizar petición POST al endpoint
        var resultado = mockMvc.perform(
            post("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        );

        // ASSERT
        resultado
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.tipoPedido").value("NACIONAL"));
    }

    @Test
    void postPedidos_stockInsuficiente_retornaCodigoError() throws Exception {

        // ARRANGE
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("test@t.cl");
        req.setClienteNombre("Test Cliente");
        req.setTipoPedido("NACIONAL");
        req.setDestino("Santiago");
        req.setProductoId(5L);
        req.setCantidad(999);
        req.setTotal(100.0);

        // Simular que la fachada lanza excepción por stock insuficiente
        when(logisticaFacade.procesarCreacionPedido(any()))
            .thenThrow(new RuntimeException(
                "Stock insuficiente o servicio de inventario no disponible"));

        // ACT
        var resultado = mockMvc.perform(
            post("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        );

        // ASSERT
        // El status debe ser 4xx (400, 409 según el handler configurado)
        resultado.andExpect(status().is4xxClientError());
    }

    @Test
    void getPedidosInternoPorEmail_emailConPedidos_retorna200ConLista() throws Exception {

        // ARRANGE
        PedidoDatosDTO dto = PedidoDatosDTO.builder()
                .id(42L).clienteNombre("Comercial Andina Ltda.").total(15990.0)
                .status("ENTREGADO").tipoPedido("NACIONAL").destino("Valparaíso")
                .build();
        when(orderService.getPedidosByUserEmail("dueña@pyme.cl")).thenReturn(List.of(dto));

        // ACT
        var resultado = mockMvc.perform(get("/pedidos/interno/por-email/dueña@pyme.cl"));

        // ASSERT
        resultado.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].destino").value("Valparaíso"));
    }

    @Test
    void getPedidosInternoPorEmail_emailSinPedidos_retorna200ConListaVacia() throws Exception {

        // ARRANGE
        when(orderService.getPedidosByUserEmail("nadie@pyme.cl")).thenReturn(List.of());

        // ACT
        var resultado = mockMvc.perform(get("/pedidos/interno/por-email/nadie@pyme.cl"));

        // ASSERT
        resultado.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
