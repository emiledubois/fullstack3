package com.smartlogix.pedidos.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private Long   userId;
    private String userEmail;
    private Double total;
}
