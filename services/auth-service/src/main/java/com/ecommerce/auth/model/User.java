package com.ecommerce.auth.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String role;

    // Nullable: cuentas creadas antes de este cambio quedan con NULL (sin
    // backfill — ddl-auto=update, no hay Flyway/Liquibase en este repo).
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Ciclo de vida de la cuenta — derecho de cancelación ARCO+ (Ley 21.719).
    // Nullable: NULL en filas preexistentes se trata como "ACTIVA" en código
    // (mismo criterio de "sin backfill" que createdAt).
    // Valores: ACTIVA / CANCELACION_EN_PROGRESO / CANCELADA.
    @Column(name = "status")
    private String status;

    @Column(name = "cancelacion_solicitada_en")
    private LocalDateTime cancelacionSolicitadaEn;

    @Column(name = "cancelacion_completada_en")
    private LocalDateTime cancelacionCompletadaEn;

    // Derecho de oposición ARCO+ (Ley 21.719) — Boolean wrapper (no
    // primitivo): NULL en filas preexistentes se trata como "false" en
    // código, mismo criterio que createdAt/status.
    @Column(name = "oposicion_procesamiento")
    private Boolean oposicionProcesamiento;

    @Column(name = "oposicion_registrada_en")
    private LocalDateTime oposicionRegistradaEn;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
