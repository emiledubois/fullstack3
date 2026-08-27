package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.client.InventarioClient;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.dto.PedidoDatosDTO;
import com.smartlogix.pedidos.event.PedidoAprobadoEvent;
import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import com.smartlogix.pedidos.saga.SagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository       orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InventarioClient       inventarioClient;
    @Mock private PedidoFactory          pedidoFactory;
    @Mock private SagaOrchestrator       sagaOrchestrator;
    @InjectMocks private OrderService    orderService;

    @Test
    void crearPedidoNacionalExitoso_retornaOrderDTOConEstadoPendiente() {

        //ARRANGE 
        // Preparar la solicitud con tipo NACIONAL y producto válido
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("cliente@test.cl");
        req.setClienteNombre("Pedro Soto");
        req.setTotal(999.0);
        req.setTipoPedido("NACIONAL");
        req.setDestino("Santiago");
        req.setProductoId(1L);
        req.setCantidad(2);

        // Simular que el Circuit Breaker confirma que hay stock disponible
        when(inventarioClient.verificarStock(1L, 2)).thenReturn(true);

        // Simular que PedidoFactory crea un pedido con los datos correctos
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId(10L);
        pedidoMock.setStatus("PENDIENTE");
        pedidoMock.setTipoPedido("NACIONAL");
        pedidoMock.setUserEmail("cliente@test.cl");
        pedidoMock.setTotal(999.0);
        when(pedidoFactory.crearPedido(req)).thenReturn(pedidoMock);

        // Simular que el repositorio guarda y retorna el pedido con ID
        when(orderRepository.save(pedidoMock)).thenReturn(pedidoMock);

        // ACT 
        // Ejecutar la creación del pedido
        OrderDTO resultado = orderService.createOrder(req);

        // ASSERT
        // Verificar que el resultado no es nulo
        assertNotNull(resultado);

        // Verificar que el estado es PENDIENTE
        assertEquals("PENDIENTE", resultado.getStatus());

        // Verificar que el tipo de pedido es NACIONAL
        assertEquals("NACIONAL", resultado.getTipoPedido());

        // Verificar que el evento fue publicado exactamente una vez
        verify(eventPublisher, times(1))
            .publishEvent(any(PedidoAprobadoEvent.class));
    }

    @Test
    void crearPedido_stockInsuficiente_lanzaExcepcionYNoGuarda() {

        // ARRANGE
        // Preparar solicitud con producto y cantidad
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setProductoId(5L);
        req.setCantidad(100);
        req.setTotal(500.0);

        // Simular que el Circuit Breaker retorna falso (sin stock)
        when(inventarioClient.verificarStock(5L, 100)).thenReturn(false);

        // ACT
        // Ejecutar y capturar la excepción esperada
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> orderService.createOrder(req));

        // ASSERT
        // Verificar que el mensaje de error es el esperado
        assertTrue(ex.getMessage().contains("insuficiente"));

        // Verificar que el repositorio NO fue llamado para guardar
        verify(orderRepository, never()).save(any());

        // Verificar que no se publicó ningún evento
        verify(eventPublisher, never()).publishEvent(any());
    }


    @Test
    void crearPedidoInternacional_factoryAgregaObservacionesAduaneras() {

        // ARRANGE
        // Preparar solicitud con tipo INTERNACIONAL
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setTipoPedido("INTERNACIONAL");
        req.setDestino("Buenos Aires");
        req.setProductoId(2L);
        req.setCantidad(1);
        req.setTotal(1500.0);
        req.setUserEmail("intl@test.cl");

        // Simular stock disponible
        when(inventarioClient.verificarStock(2L, 1)).thenReturn(true);

        // Simular que PedidoFactory crea pedido INTERNACIONAL con observaciones
        Pedido pedidoMock = new Pedido();
        pedidoMock.setId(20L);
        pedidoMock.setStatus("PENDIENTE");
        pedidoMock.setTipoPedido("INTERNACIONAL");
        pedidoMock.setUserEmail("intl@test.cl");
        pedidoMock.setTotal(1500.0);
        // La fábrica enriquece el pedido con la observación aduanera
        pedidoMock.setObservaciones("Pedido internacional — requiere documentación aduanera");
        when(pedidoFactory.crearPedido(req)).thenReturn(pedidoMock);
        when(orderRepository.save(pedidoMock)).thenReturn(pedidoMock);

        // ACT
        OrderDTO resultado = orderService.createOrder(req);

        // ASSERT 
        // Verificar que el tipo es INTERNACIONAL
        assertEquals("INTERNACIONAL", resultado.getTipoPedido());

        // Verificar que las observaciones contienen la indicación aduanera
        assertNotNull(resultado.getObservaciones());
        assertTrue(resultado.getObservaciones().contains("aduanera"));
    }


    @Test
    void getPedidosByUserEmail_emailConPedidos_retornaListaMapeadaSinUserIdNiUserEmail() {

        // ARRANGE
        Pedido pedido = Pedido.builder()
                .id(42L).userId(1L).userEmail("dueña@pyme.cl")
                .clienteNombre("Comercial Andina Ltda.").total(15990.0)
                .status("ENTREGADO").tipoPedido("NACIONAL").destino("Valparaíso")
                .productoId(7L).cantidad(3).build();
        when(orderRepository.findByUserEmail("dueña@pyme.cl")).thenReturn(List.of(pedido));

        // ACT
        List<PedidoDatosDTO> resultado = orderService.getPedidosByUserEmail("dueña@pyme.cl");

        // ASSERT
        assertEquals(1, resultado.size());
        assertEquals(42L, resultado.get(0).getId());
        assertEquals("Valparaíso", resultado.get(0).getDestino());
    }

    @Test
    void getPedidosByUserEmail_emailSinPedidos_retornaListaVacia() {

        // ARRANGE
        when(orderRepository.findByUserEmail("nadie@pyme.cl")).thenReturn(List.of());

        // ACT
        List<PedidoDatosDTO> resultado = orderService.getPedidosByUserEmail("nadie@pyme.cl");

        // ASSERT
        assertTrue(resultado.isEmpty());
    }

}
