package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Wire-format body of POST /auth/login. The JWT itself never appears here —
// it is only ever delivered via the httpOnly Set-Cookie header.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String email;
    private String role;
    private Instant expiresAt;
}
