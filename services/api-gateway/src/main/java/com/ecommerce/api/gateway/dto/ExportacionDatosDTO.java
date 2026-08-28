package com.ecommerce.api.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// Envoltorio de exportación — derecho de portabilidad ARCO+ (Ley 21.719).
// Envuelve la MISMA agregación de UsuarioDatosDTO (derecho de acceso, sin
// duplicar lógica) con metadatos de versión/tipo de solicitud, ver
// arco-remaining-rights.md §4.3.
@Data
@Builder
public class ExportacionDatosDTO {
    private String formatoVersion;
    private String tipoSolicitud;
    private String titular;
    private LocalDateTime generadoEn;
    private UsuarioDatosDTO datos;
}
