package com.smartlogix.inventario.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "productos")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El SKU no puede estar vacío")
    @Column(unique = true, nullable = false)
    private String sku;            // Código único del producto en SmartLogix

    @NotBlank
    @Column(nullable = false)
    private String nombre;

    @NotNull @DecimalMin("0.0")
    @Column(nullable = false)
    private Double precioUnitario;

    @Min(0) @Column(nullable = false)
    private Integer stockActual;

    // SmartLogix — umbral para alertas de desabastecimiento (RT-01)
    @Min(0) @Column(nullable = false)
    private Integer umbralMinimo;   // Cuando stockActual < umbralMinimo → alerta

    private String bodega;          // Ubicación física en la bodega de la PYME
    private String descripcion;

    // Computed helper — usado por el Circuit Breaker endpoint
    public boolean tieneStock(int cantidad) {
        return this.stockActual >= cantidad;
    }
}

