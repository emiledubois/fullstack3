package com.ecommerce.api.gateway.config;

import com.ecommerce.api.gateway.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.server.mvc.handler.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.function.*;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import org.springframework.web.servlet.function.RouterFunctions;

@Configuration
public class GatewayConfig {

    @Autowired
    private AuthFilter authFilter;

    // PUBLIC — no JWT required
    // /api/auth/login  →  auth-service:8081/auth/login  (stripPrefix(1) removes /api)
    @Bean
    public RouterFunction<ServerResponse> authRoute() {
        return GatewayRouterFunctions.route("auth")
                .route(path("/api/auth/**"), HandlerFunctions.http("http://auth-service:8081"))
                .filter(stripPrefix(1))
                .build();
    }

    // PROTECTED — JWT required via authFilter
    // /api/inventario/productos  →  ms-inventario:8082/inventario/productos
    @Bean
    public RouterFunction<ServerResponse> inventarioRoute() {
        return GatewayRouterFunctions.route("inventario")
                .route(path("/api/inventario/**"), HandlerFunctions.http("http://ms-inventario:8082"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    // /api/pedidos/**  →  ms-pedidos:8083/pedidos/**
    @Bean
    public RouterFunction<ServerResponse> pedidosRoute() {
        return GatewayRouterFunctions.route("pedidos")
                .route(path("/api/pedidos/**"), HandlerFunctions.http("http://ms-pedidos:8083"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    // /api/envios/**  →  ms-envios:8084/envios/**
    @Bean
    public RouterFunction<ServerResponse> enviosRoute() {
        return GatewayRouterFunctions.route("envios")
                .route(path("/api/envios/**"), HandlerFunctions.http("http://ms-envios:8084"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    // Ruta para la Saga — apunta al mismo ms-pedidos, protegida con JWT
    @Bean
    public RouterFunction<ServerResponse> sagasRoute() {
        return GatewayRouterFunctions.route("sagas")
                .route(path("/api/sagas/**"), HandlerFunctions.http("http://ms-pedidos:8083"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    /**
     * Ruta para el WebHub/BFF.
     * /api/dashboard NO se enruta a ningún microservicio —
     * lo atiende DashboardController directamente en el gateway.
     * stripPrefix(1) elimina /api, dejando /dashboard
     * que mapea al @RequestMapping("/dashboard") del controller.
     */
    @Bean
    public RouterFunction<ServerResponse> dashboardRoute() {
        return GatewayRouterFunctions.route("dashboard")
                .route(path("/api/dashboard/**"), HandlerFunctions.http("http://localhost:8080"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> pagosRoute() {
        return GatewayRouterFunctions.route("pagos")
                .route(path("/api/pagos/**"), HandlerFunctions.http("http://ms-pagos:8086"))
                .filter(stripPrefix(1))
                .filter(authFilter)  // JWT requerido para /api/pagos/crear y /api/pagos/{id}
                .build();
    }

    // IMPORTANTE: El webhook de Flow va por una ruta separada SIN authFilter
    // Flow llama al webhook sin JWT — se excluye del filtro
    @Bean
    public RouterFunction<ServerResponse> webhookFlowRoute() {
        return GatewayRouterFunctions.route("webhook-flow")
                .route(path("/api/pagos/webhook/flow"), HandlerFunctions.http("http://ms-pagos:8086"))
                .filter(stripPrefix(1))
                // Sin authFilter — Flow no puede enviar JWT
                .build();
    }
}
