package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.client.InventarioClient;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository          orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InventarioClient          inventarioClient;
    @Mock private PedidoFactory             pedidoFactory;
    @InjectMocks private OrderService       orderService;

    @Test @DisplayName("createOrder crea pedido cuando hay stock")
    void createOrder_withStock_succeeds() {
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L); req.setTotal(500.0);
        req.setProductoId(1L); req.setCantidad(2);
        req.setTipoPedido("NACIONAL"); req.setUserEmail("a@b.com");

        when(inventarioClient.verificarStock(1L, 2)).thenReturn(true);
        Pedido pedido = Pedido.builder().userId(1L).total(500.0).tipoPedido("NACIONAL").build();
        when(pedidoFactory.crearPedido(req)).thenReturn(pedido);
        Pedido saved = Pedido.builder().id(1L).userId(1L).total(500.0)
            .status("PENDIENTE").tipoPedido("NACIONAL").build();
        when(orderRepository.save(pedido)).thenReturn(saved);

        assertThat(orderService.createOrder(req).getStatus()).isEqualTo("PENDIENTE");
        verify(inventarioClient).verificarStock(1L, 2);
        verify(eventPublisher).publishEvent(any());
    }

    @Test @DisplayName("createOrder lanza excepción cuando Circuit Breaker indica sin stock")
    void createOrder_noStock_throws() {
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setProductoId(1L); req.setCantidad(100);
        when(inventarioClient.verificarStock(1L, 100)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Stock insuficiente");
    }

    @Test @DisplayName("getOrderById lanza excepción si no existe")
    void getById_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getOrderById(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Pedido no encontrado");
    }

    @Test @DisplayName("getAllOrders devuelve lista vacía si no hay pedidos")
    void getAllOrders_empty() {
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        assertThat(orderService.getAllOrders()).isEmpty();
    }
}
