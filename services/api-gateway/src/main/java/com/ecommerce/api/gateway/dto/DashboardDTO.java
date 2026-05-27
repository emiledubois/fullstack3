package com.ecommerce.api.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

// El DTO raíz que el frontend recibe en UNA sola petición.
// Reemplaza 4 llamadas HTTP independientes del Dashboard.jsx anterior.
@Data
@Builder
public class DashboardDTO {
    private MetricasDTO         metricas;         // stats calculadas
    private List<ProductoResumen> productos;       // todos los productos
    private List<ProductoResumen> alertasStock;    // solo los bajo umbral
    private List<PedidoResumen>  ultimosPedidos;  // últimos 5
    private List<EnvioResumen>   ultimosEnvios;   // últimos 3
    private LocalDateTime        generadoEn;      // timestamp del BFF
    private String               estadoServicios; // OK / PARCIAL / ERROR
}
