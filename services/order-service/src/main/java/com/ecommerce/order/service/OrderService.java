package com.ecommerce.order.service;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;
import com.ecommerce.order.event.OrderCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;



@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderDTO createOrder(CreateOrderRequest req) {
        if (req.getUserId() == null || req.getTotal() == null)
            throw new RuntimeException("userId y total son requeridos");

        Order order = Order.builder()
                .userId(req.getUserId())
                .userEmail(req.getUserEmail())
                .total(req.getTotal())
                .build();

        return OrderDTO.from(orderRepository.save(order));
       	eventPublisher.publishEvent(
          new OrderCreatedEvent(this, result.getId(), req.getUserEmail(),
	  result.getTotal())	);

        // TODO Fase 2: publicar OrderCreatedEvent → notification-service
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
