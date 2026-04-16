package com.smartlogix.inventario.dto;

import com.smartlogix.inventario.model.Producto;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductDTO {
    private Long    id;
    private String  sku;
    private String  nombre;
    private Double  precioUnitario;
    private Integer stockActual;
    private Integer umbralMinimo;
    private String  bodega;
    private String  descripcion;

    public static ProductDTO from(Producto p) {
        return ProductDTO.builder()
                .id(p.getId()).sku(p.getSku()).nombre(p.getNombre())
                .precioUnitario(p.getPrecioUnitario()).stockActual(p.getStockActual())
                .umbralMinimo(p.getUmbralMinimo()).bodega(p.getBodega())
                .descripcion(p.getDescripcion())
                .build();
    }
}
