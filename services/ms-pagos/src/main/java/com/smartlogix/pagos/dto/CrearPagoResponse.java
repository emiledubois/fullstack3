package com.smartlogix.pagos.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CrearPagoResponse {
    private Long   pagoId;
    private String urlPago;       // url + "?token=" + token — para redirigir al usuario
    private String flowToken;
    private Long   flowOrder;
    private String estado;
}
