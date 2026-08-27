package com.smartlogix.pedidos.dto;

import com.smartlogix.pedidos.model.Pedido;
import lombok.*;
import java.time.LocalDateTime;

// Respuesta de GET /pedidos/interno/por-email/{email} — derecho de acceso
// ARCO+ (arco-acceso-personal-data.md §3.3). Deliberadamente NO incluye
// userId/userEmail (ya conocidos por el propio dueño) ni observaciones
// (puede contener notas operativas internas, ver diseño §9 punto 4).
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PedidoDatosDTO {
    private Long          id;
    private String        clienteNombre;
    private Double        total;
    private String        status;
    private String        tipoPedido;
    private String        destino;
    private Long           productoId;
    private Integer       cantidad;
    private LocalDateTime creadoEn;

    public static PedidoDatosDTO from(Pedido p) {
        return PedidoDatosDTO.builder()
                .id(p.getId()).clienteNombre(p.getClienteNombre())
                .total(p.getTotal()).status(p.getStatus())
                .tipoPedido(p.getTipoPedido()).destino(p.getDestino())
                .productoId(p.getProductoId()).cantidad(p.getCantidad())
                .creadoEn(p.getCreadoEn())
                .build();
    }
}
