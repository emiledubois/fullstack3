package com.ecommerce.notification.service.listener;

@Service @Slf4j
public class NotificationListener {

    @EventListener @Async
    public void handlePedidoAprobado(PedidoAprobadoEvent event) {
        log.info("[SmartLogix] 📧 Notificación enviada a {} — Pedido #{} ({}) aprobado. Total: ${}",
                 event.getUserEmail(), event.getPedidoId(),
                 event.getTipoPedido(), event.getTotal());
        // En producción: JavaMailSender.send(to, "Pedido aprobado", body)
    }
}
