package com.ecommerce.api.gateway.filter;

import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

// Patrón arquitectónico: el Gateway centraliza la autenticación
// Ningún microservicio necesita validar JWT — solo el Gateway lo hace
@Component
public class AuthFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public ServerResponse filter(ServerRequest request,
                                 HandlerFunction<ServerResponse> next) throws Exception {
        String authHeader = request.headers().firstHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("Token no proporcionado");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("Token inválido o expirado");
        }

        // Propagar el email extraído del JWT a los servicios downstream
        String email = jwtUtil.extractEmail(token);
        ServerRequest modifiedRequest = ServerRequest.from(request)
                .header("X-User-Email", email)
                .build();

        return next.handle(modifiedRequest);
    }
}
