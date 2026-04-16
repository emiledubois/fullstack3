package com.smartlogix.envios.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "envios")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Envio {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;         // FK lógica (no FK real — Database-per-Service)

    @Column(nullable = false)
    private String status;         // CREADO→ASIGNADO→EN_RUTA→ENTREGADO

    // Factory Method: tipo determina qué clase concreta usa EnvioFactory
    @Column(nullable = false)
    private String tipoEnvio;      // "TERRESTRE", "EXPRESS", "COURIER_EXTERNO"

    private String transportista;  // Nombre de la empresa de transporte
    private String guiaDespecho;   // Número de guía del transportista
    private String rutaDescripcion;
    private String destino;

    @Column(name = "fecha_estimada_entrega")
    private LocalDateTime fechaEstimadaEntrega;

    @Column(name = "created_at")
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        this.creadoEn = LocalDateTime.now();
        if (this.status == null) this.status = "CREADO";
    }
}
