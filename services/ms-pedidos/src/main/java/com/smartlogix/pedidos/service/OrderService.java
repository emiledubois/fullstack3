package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.client.InventarioClient;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.event.PedidoAprobadoEvent;
import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository     orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InventarioClient    inventarioClient;
    private final PedidoFactory       pedidoFactory;

    public OrderDTO createOrder(CreatePedidoRequest req) {
        // Circuit Breaker: verificar stock en ms-inventario
        if (req.getProductoId() != null) {
            int qty = req.getCantidad() != null ? req.getCantidad() : 1;
            Boolean hayStock = inventarioClient.verificarStock(req.getProductoId(), qty);
            if (Boolean.FALSE.equals(hayStock)) {
                throw new RuntimeException("Stock insuficiente o servicio de inventario no disponible");
            }
        }

        // Factory Method: crea PedidoNacional o PedidoInternacional
        Pedido pedido = pedidoFactory.crearPedido(req);
        Pedido saved  = orderRepository.save(pedido);

        // Observer Pattern: publicar evento → notification-service
        eventPublisher.publishEvent(
            new PedidoAprobadoEvent(this, saved.getId(),
                saved.getUserEmail(), saved.getTotal(), saved.getTipoPedido()));

        log.info("Pedido creado: id={} tipo={}", saved.getId(), saved.getTipoPedido());
        return OrderDTO.from(saved);
    }

    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream().map(OrderDTO::from).collect(Collectors.toList());
    }

    public OrderDTO getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(OrderDTO::from)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + id));
    }
}
