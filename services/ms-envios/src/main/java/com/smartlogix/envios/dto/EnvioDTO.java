package com.smartlogix.envios.dto;

import com.smartlogix.envios.model.Envio;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EnvioDTO {
    private Long          id;
    private Long          pedidoId;
    private String        status;
    private String        tipoEnvio;
    private String        transportista;
    private String        guiaDespecho;
    private String        rutaDescripcion;
    private String        destino;
    private LocalDateTime fechaEstimadaEntrega;
    private LocalDateTime creadoEn;

    public static EnvioDTO from(Envio e) {
        return EnvioDTO.builder()
                .id(e.getId()).pedidoId(e.getPedidoId())
                .status(e.getStatus()).tipoEnvio(e.getTipoEnvio())
                .transportista(e.getTransportista()).guiaDespecho(e.getGuiaDespecho())
                .rutaDescripcion(e.getRutaDescripcion()).destino(e.getDestino())
                .fechaEstimadaEntrega(e.getFechaEstimadaEntrega())
                .creadoEn(e.getCreadoEn())
                .build();
    }
}
