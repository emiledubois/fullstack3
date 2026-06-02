package com.smartlogix.pagos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencia al pedido en ms-pedidos (FK lógica, no referencial)
    @Column(nullable = false)
    private Long pedidoId;

    // commerceOrder enviado a Flow — usar pedidoId como referencia
    // Fuente: developers.flow.cl — Creación de orden (parámetro commerceOrder)
    @Column(nullable = false, unique = true)
    private String commerceOrder;

    // Token retornado por Flow en payment/create
    // Fuente: developers.flow.cl — Creación de orden (respuesta: token)
    private String flowToken;

    // flowOrder retornado por Flow
    private Long flowOrder;

    // Estado del pago — basado en los status de Flow:
    // Fuente: developers.flow.cl — Estado de orden
    // 1=PENDIENTE, 2=PAGADO, 3=RECHAZADO, 4=ANULADO
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    private Double monto;
    private String email;
    private String urlPago; // url + "?token=" + token

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();

    private LocalDateTime confirmadoEn;

    public enum EstadoPago {
        INICIADO,        // antes de redirigir al usuario
        PENDIENTE,       // Flow status 1
        PAGADO,          // Flow status 2
        RECHAZADO,       // Flow status 3
        ANULADO          // Flow status 4
    }
}
