package com.ecommerce.api.gateway.config;

import com.ecommerce.api.gateway.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayConfig {

    @Autowired
    private AuthFilter authFilter;

    // Ruta pública: /api/auth/** → auth-service (sin validación JWT)
    @Bean
    public RouterFunction<ServerResponse> authRoute() {
        return GatewayRouterFunctions.route("auth-public")
                .route(path("/api/auth/**"),
                       HandlerFunctions.http("http://auth-service:8081"))
                .filter(stripPrefix(2))
                .build();
    }

    // Rutas protegidas: aplican AuthFilter
    @Bean
    public RouterFunction<ServerResponse> productRoute() {
        return GatewayRouterFunctions.route("product-service")
                .route(path("/api/products/**"),
                       HandlerFunctions.http("http://product-service:8082"))
                .filter(stripPrefix(2))
                .filter(authFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderRoute() {
        return GatewayRouterFunctions.route("order-service")
                .route(path("/api/orders/**"),
                       HandlerFunctions.http("http://order-service:8083"))
                .filter(stripPrefix(2))
                .filter(authFilter)
                .build();
    }
}
