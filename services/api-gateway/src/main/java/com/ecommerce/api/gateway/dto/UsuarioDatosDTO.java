package com.ecommerce.api.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

// Respuesta de GET /api/usuarios/me/datos — derecho de acceso ARCO+
// (Ley 21.719, COMPLIANCE_CL.md §4.2). Agrega auth-service + ms-pedidos +
// ms-envios en una sola respuesta, análogo a DashboardDTO.
@Data
@Builder
public class UsuarioDatosDTO {
    private LocalDateTime         generadoEn;
    private String                estadoAgregacion; // OK / PARCIAL / ERROR
    private CuentaDTO              cuenta;           // null si no se pudo resolver
    private List<PedidoDatosDTO>  pedidos;
    private List<EnvioDatosDTO>   envios;
}
