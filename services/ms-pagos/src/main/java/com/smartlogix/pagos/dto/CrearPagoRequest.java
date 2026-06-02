package com.smartlogix.pagos.dto;

import lombok.Data;

@Data
public class CrearPagoRequest {
    private Long   pedidoId;
    private Double monto;
    private String email;
    private String descripcion;   // subject en Flow
}
