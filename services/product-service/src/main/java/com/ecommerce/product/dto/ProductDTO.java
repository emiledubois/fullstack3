package com.ecommerce.product.dto;

import com.ecommerce.product.model.Product;
import lombok.*;

// DTO: expone solo los campos necesarios (IL 1.3 — privacidad por diseño)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductDTO {
    private Long    id;
    private String  name;
    private Double  price;
    private Integer stock;
    private String  description;

    public static ProductDTO from(Product p) {
        return ProductDTO.builder()
                .id(p.getId()).name(p.getName())
                .price(p.getPrice()).stock(p.getStock())
                .description(p.getDescription())
                .build();
    }
}
