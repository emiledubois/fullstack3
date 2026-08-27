package com.smartlogix.envios.dto;

import com.smartlogix.envios.model.Envio;
import lombok.*;
import java.time.LocalDateTime;

// Respuesta de GET /envios/interno/por-pedidos — derecho de acceso ARCO+
// (arco-acceso-personal-data.md §3.4). Deliberadamente NO incluye
// guiaDespecho/rutaDescripcion (detalles operativos del transportista, no
// parte del contrato de la API §3.1).
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EnvioDatosDTO {
    private Long          id;
    private Long          pedidoId;
    private String        status;
    private String        tipoEnvio;
    private String        transportista;
    private String        destino;
    private LocalDateTime fechaEstimadaEntrega;
    private LocalDateTime creadoEn;

    public static EnvioDatosDTO from(Envio e) {
        return EnvioDatosDTO.builder()
                .id(e.getId()).pedidoId(e.getPedidoId())
                .status(e.getStatus()).tipoEnvio(e.getTipoEnvio())
                .transportista(e.getTransportista()).destino(e.getDestino())
                .fechaEstimadaEntrega(e.getFechaEstimadaEntrega())
                .creadoEn(e.getCreadoEn())
                .build();
    }
}
