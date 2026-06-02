package com.smartlogix.pedidos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// "PENDIENTE_PAGO"  — creado, esperando confirmación de Flow
// "PAGADO"          — Flow confirmó el pago, Saga disparada
// "PAGO_RECHAZADO"  — Flow rechazó el pago
// "PAGO_ANULADO"    — Flow anuló el pago

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

    @Column
    private Long productoId;

    @Column
    private Integer cantidad;

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
