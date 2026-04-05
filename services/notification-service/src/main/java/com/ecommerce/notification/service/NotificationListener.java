package com.ecommerce.notification.service;

import com.ecommerce.order.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationListener {

    // Observer Pattern — este método se activa cuando OrderService publica el evento
    @EventListener
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("=== NOTIFICACIÓN === Pedido #{} confirmado para {}. Total: ${}",
                 event.getOrderId(), event.getUserEmail(), event.getTotal());
        
    }
}

