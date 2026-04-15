package com.smartlogix.pedidos.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PedidoAprobadoEvent extends ApplicationEvent {
    private final Long pedidoId;
    private final String userEmail;
    private final Double total;
    private final String tipoPedido;

    public PedidoAprobadoEvent(Object src, Long pedidoId,
                                String userEmail, Double total, String tipo) {
        super(src);
        this.pedidoId = pedidoId;
        this.userEmail = userEmail;
        this.total = total;
        this.tipoPedido = tipo;
    }
}
