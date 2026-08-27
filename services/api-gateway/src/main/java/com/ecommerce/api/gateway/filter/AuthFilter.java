package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.function.*;

@Component
public class AuthFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final String COOKIE_NAME = "sl_jwt";

    @Autowired private JwtUtil jwtUtil;

    @Override
    public ServerResponse filter(ServerRequest req, HandlerFunction<ServerResponse> next)
            throws Exception {
        String token = extractToken(req);
        if (token == null)
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body("Token requerido");

        if (!jwtUtil.isValid(token))
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body("Token inválido");

        // Strip any client-supplied X-User-Email before setting the value derived
        // from the verified JWT, so a caller can't spoof another user's identity
        // by sending its own X-User-Email header (OWASP A01: Broken Access Control).
        ServerRequest modified = ServerRequest.from(req)
            .headers(headers -> headers.remove("X-User-Email"))
            .header("X-User-Email", jwtUtil.extractEmail(token)).build();
        return next.handle(modified);
    }

    private String extractToken(ServerRequest req) {
        MultiValueMap<String, Cookie> cookies = req.cookies();
        Cookie cookie = cookies.getFirst(COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }
}
