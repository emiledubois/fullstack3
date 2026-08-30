package com.smartlogix.pedidos.saga;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "saga_estado")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SagaEstado {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID sagaId;

    @Column(nullable = false, length = 50)
    private String tipo;              // "CREAR_PEDIDO"

    @Column(nullable = false, length = 50)
    private String pasoActual;        // nombre del paso en ejecución

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSaga estado;        // INICIADA, EN_PROGRESO, COMPLETADA, COMPENSANDO, FALLIDA

    // El request original serializado como JSONB en PostgreSQL
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean stockReservado = false;

    private Long pedidoId;
    private Long envioId;

    // Nullable — permite reconstruir qué líneas de log corresponden a esta
    // fila mucho después de que los logs hayan rotado (observability-
    // correlation-ids.md §5.5, COMPLIANCE_CL.md §4.4).
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(columnDefinition = "text")
    private String ultimoError;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    public enum EstadoSaga {
        INICIADA, EN_PROGRESO, COMPLETADA, COMPENSANDO, FALLIDA
    }
}
