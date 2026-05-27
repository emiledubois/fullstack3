package com.ecommerce.api.gateway.dto;

import lombok.Builder;
import lombok.Data;

// Métricas calculadas en el gateway a partir de los datos agregados.
// Este es el valor diferencial del BFF: el frontend no calcula nada.
@Data
@Builder
public class MetricasDTO {
    private int    totalProductos;
    private int    productosConAlertaStock;  // stockActual < umbralMinimo
    private int    totalPedidos;
    private int    pedidosPendientes;
    private int    pedidosConfirmados;
    private int    totalEnvios;
    private int    enviosEnRuta;
    private double valorTotalInventario;     // suma precioUnitario * stockActual
}
