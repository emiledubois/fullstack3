package com.smartlogix.inventario.dto;

import lombok.*;
import java.util.List;

// DTO de respuesta del endpoint de alertas — incluye metadata de la estrategia usada
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertaResponseDTO {
    private String          estrategia;
    private String          descripcionEstrategia;
    private int             totalProductos;
    private List<ProductDTO> alertas;
}
