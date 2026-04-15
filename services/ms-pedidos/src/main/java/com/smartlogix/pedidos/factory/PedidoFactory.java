package com.smartlogix.pedidos.factory;

import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.model.Pedido;
import org.springframework.stereotype.Component;

@Component
public class PedidoFactory {

    public Pedido crearPedido(CreatePedidoRequest req) {
        String tipo = req.getTipoPedido() != null
            ? req.getTipoPedido().toUpperCase() : "NACIONAL";

        return switch (tipo) {
            case "INTERNACIONAL" -> crearPedidoInternacional(req);
            default              -> crearPedidoNacional(req);
        };
    }

    private Pedido crearPedidoNacional(CreatePedidoRequest req) {
        return Pedido.builder()
            .userId(req.getUserId())
            .userEmail(req.getUserEmail())
            .clienteNombre(req.getClienteNombre())
            .total(req.getTotal())
            .tipoPedido("NACIONAL")
            .destino(req.getDestino())
            .build();
    }

    private Pedido crearPedidoInternacional(CreatePedidoRequest req) {
        // Pedido internacional tiene campos adicionales de SmartLogix
        return Pedido.builder()
            .userId(req.getUserId())
            .userEmail(req.getUserEmail())
            .clienteNombre(req.getClienteNombre())
            .total(req.getTotal())
            .tipoPedido("INTERNACIONAL")
            .destino(req.getDestino())
            .observaciones("Pedido internacional — requiere documentación aduanera")
            .build();
    }
}
