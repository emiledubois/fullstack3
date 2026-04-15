package com.smartlogix.pedidos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "pedidos")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Pedido {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;
    private String userEmail;
    private String clienteNombre;     // Nombre del cliente final de la PYME

    @Column(nullable = false)
    private Double total;

    // Estados del flujo logístico SmartLogix
    @Column(nullable = false)
    private String status;            // PENDIENTE→APROBADO→EN_ENVIO→ENTREGADO→CANCELADO

    // Factory Method: tipo de pedido para PedidoFactory
    @Column(nullable = false)
    private String tipoPedido;        // "NACIONAL" o "INTERNACIONAL"

    private String destino;           // Ciudad/país de destino
    private String observaciones;

    @Column(name = "created_at")
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        this.creadoEn = LocalDateTime.now();
        if (this.status == null) this.status = "PENDIENTE";
    }
}
