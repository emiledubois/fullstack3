package com.ecommerce.api.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) { return false; }
    }

    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractRole(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload().get("role", String.class);
    }

    // Lee el claim exp del token recién emitido por auth-service en vez de
    // duplicar jwt.expiration en este servicio — así el Max-Age de la cookie
    // rotada por rectificación nunca puede desincronizarse del vencimiento
    // real del JWT (mismo motivo que AuthService/JwtUtil.extractExpiration
    // en auth-service).
    public long getRemainingSeconds(String token) {
        long expMillis = Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload().getExpiration().getTime();
        return Math.max(0, (expMillis - System.currentTimeMillis()) / 1000);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
