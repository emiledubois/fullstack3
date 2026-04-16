package com.smartlogix.pedidos.dto;

import com.smartlogix.pedidos.model.Pedido;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderDTO {
    private Long          id;
    private Long          userId;
    private String        userEmail;
    private String        clienteNombre;
    private Double        total;
    private String        status;
    private String        tipoPedido;
    private String        destino;
    private String        observaciones;
    private LocalDateTime creadoEn;

    public static OrderDTO from(Pedido p) {
        return OrderDTO.builder()
                .id(p.getId()).userId(p.getUserId())
                .userEmail(p.getUserEmail()).clienteNombre(p.getClienteNombre())
                .total(p.getTotal()).status(p.getStatus())
                .tipoPedido(p.getTipoPedido()).destino(p.getDestino())
                .observaciones(p.getObservaciones()).creadoEn(p.getCreadoEn())
                .build();
    }
}

