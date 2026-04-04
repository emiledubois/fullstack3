package com.ecommerce.order.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private Long   userId;
    private String userEmail;
    private Double total;
}
