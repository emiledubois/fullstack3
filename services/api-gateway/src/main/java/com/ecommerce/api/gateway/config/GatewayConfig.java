package com.ecommerce.api.gateway.config;

import com.ecommerce.api.gateway.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.server.mvc.handler.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.function.*;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayConfig {

    @Autowired private AuthFilter authFilter;

    @Bean public RouterFunction<ServerResponse> authRoute() {
        return GatewayRouterFunctions.route("auth")
            .route(path("/api/auth/**"), HandlerFunctions.http("http://auth-service:8081"))
            .filter(stripPrefix(2)).build();  // SIN authFilter: ruta pública
    }

    @Bean public RouterFunction<ServerResponse> inventarioRoute() {
        return GatewayRouterFunctions.route("inventario")
            .route(path("/api/inventario/**"), HandlerFunctions.http("http://ms-inventario:8082"))
            .filter(stripPrefix(2)).filter(authFilter).build(); // CON authFilter
    }

    @Bean public RouterFunction<ServerResponse> pedidosRoute() {
        return GatewayRouterFunctions.route("pedidos")
            .route(path("/api/pedidos/**"), HandlerFunctions.http("http://ms-pedidos:8083"))
            .filter(stripPrefix(2)).filter(authFilter).build();
    }

    @Bean public RouterFunction<ServerResponse> enviosRoute() {
        return GatewayRouterFunctions.route("envios")
            .route(path("/api/envios/**"), HandlerFunctions.http("http://ms-envios:8084"))
            .filter(stripPrefix(2)).filter(authFilter).build();
    }
}
