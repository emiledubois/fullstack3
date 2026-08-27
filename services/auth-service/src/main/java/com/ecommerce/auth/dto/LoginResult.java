package com.ecommerce.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

// Internal transfer object between AuthService and AuthController — carries
// the raw JWT so the controller can set the Set-Cookie header. Never
// serialized directly as an HTTP response body (see LoginResponse for that).
@Data
@AllArgsConstructor
public class LoginResult {

    private String token;
    private String email;
    private String role;
    private Instant expiresAt;
    private long maxAgeSeconds;
}
