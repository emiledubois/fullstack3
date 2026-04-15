package com.smartlogix.pedidos.dto;

import com.smartlogix.pedidos.model.Order;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderDTO {
    private Long          id;
    private Long          userId;
    private String        userEmail;
    private Double        total;
    private String        status;
    private LocalDateTime createdAt;

    public static OrderDTO from(Order o) {
        return OrderDTO.builder()
                .id(o.getId()).userId(o.getUserId())
                .userEmail(o.getUserEmail()).total(o.getTotal())
                .status(o.getStatus()).createdAt(o.getCreatedAt())
                .build();
    }
}
