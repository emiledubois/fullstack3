package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.*;

@Component
public class AuthFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Autowired private JwtUtil jwtUtil;

    @Override
    public ServerResponse filter(ServerRequest req, HandlerFunction<ServerResponse> next)
            throws Exception {
        String auth = req.headers().firstHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer "))
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body("Token requerido");

        String token = auth.substring(7);
        if (!jwtUtil.isValid(token))
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body("Token inválido");

        ServerRequest modified = ServerRequest.from(req)
            .header("X-User-Email", jwtUtil.extractEmail(token)).build();
        return next.handle(modified);
    }
}
