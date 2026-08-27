package com.ecommerce.api.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionDTO {
    private String email;
    private String role;
}
