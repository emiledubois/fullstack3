package com.ecommerce.order.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

// Observer Pattern — el "evento" que actúa como mensaje entre servicios
@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    private final Long   orderId;
    private final String userEmail;
    private final Double total;

    public OrderCreatedEvent(Object source, Long orderId,
                             String userEmail, Double total) {
        super(source);
        this.orderId   = orderId;
        this.userEmail = userEmail;
        this.total     = total;
    }
}
