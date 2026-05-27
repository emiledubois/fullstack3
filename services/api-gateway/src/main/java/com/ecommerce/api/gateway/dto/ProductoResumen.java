package com.ecommerce.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;


// el gateway no necesita todos los campos de Producto,
// solo los relevantes para el dashboard.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductoResumen {
    private Long    id;
    private String  sku;
    private String  nombre;
    private Integer stockActual;
    private Integer umbralMinimo;
    private Double  precioUnitario;
    private String  bodega;
}
