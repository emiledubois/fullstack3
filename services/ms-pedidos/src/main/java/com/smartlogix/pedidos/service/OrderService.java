package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.dto.*;
import com.smartlogix.pedidos.model.Order;
import com.smartlogix.pedidos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InventarioClient inventarioClient; // Circuit Breaker
    private final PedidoFactory pedidoFactory;       // Factory Method
 
    public OrderDTO createOrder(CreateOrderRequest req) {
        // Circuit Breaker: verificar stock antes de crear el pedido
        if (req.getProductoId() != null) {
            Boolean hayStock = inventarioClient.verificarStock(
                req.getProductoId(), req.getCantidad() != null ? req.getCantidad() : 1);
            if (Boolean.FALSE.equals(hayStock)) {
                throw new RuntimeException("Stock insuficiente o servicio de inventario no disponible");
            }
        }

        // Factory Method: crea el tipo correcto de Pedido
        Pedido pedido = pedidoFactory.crearPedido(req);
        Pedido saved = orderRepository.save(pedido);

        // Observer Pattern: publicar evento
        eventPublisher.publishEvent(
            new OrderCreatedEvent(this, saved.getId(), saved.getUserEmail(), saved.getTotal()));

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
