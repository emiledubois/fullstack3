package com.smartlogix.pedidos.listener;

import com.smartlogix.pedidos.event.PedidoAprobadoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Observer Pattern — escucha PedidoAprobadoEvent publicado por OrderService

@Service
@Slf4j
public class NotificationListener {

    @EventListener
    @Async
    public void handlePedidoAprobado(PedidoAprobadoEvent event) {
        log.info("[SmartLogix Notif] Pedido #{} ({}) creado para {}. Total: ${}",
                 event.getPedidoId(), event.getTipoPedido(),
                 event.getUserEmail(), event.getTotal());
        // En producción real: JavaMailSender o llamada HTTP a notification-service
    }
}
