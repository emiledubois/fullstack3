package com.ecommerce.api.gateway.config;

import com.ecommerce.api.gateway.filter.AuthFilter;
import com.ecommerce.api.gateway.filter.InternalTokenIssuerFilter;
import com.ecommerce.api.gateway.filter.StripCookieFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.server.mvc.handler.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.function.*;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.core.annotation.Order;

@Configuration
public class GatewayConfig {

    @Autowired
    private AuthFilter authFilter;

    @Autowired
    private StripCookieFilter stripCookieFilter;

    @Autowired
    private InternalTokenIssuerFilter internalTokenIssuerFilter;

    // PUBLIC — no JWT required
    // /api/auth/login  →  auth-service:8081/auth/login  (stripPrefix(1) removes /api)
    //
    // /api/auth/interno/** se EXCLUYE deliberadamente: ese sub-path solo
    // existe para que UsuarioDatosController (BFF, este mismo gateway) lo
    // llame directamente al hostname del contenedor. Si quedara enrutable
    // aquí, cualquier cliente podría pedir el email de OTRO usuario editando
    // la URL — InternalTokenIssuerFilter firmaría la petición como
    // api-gateway igual, y el nuevo InternalAuthFilter de auth-service la
    // dejaría pasar (ver arco-acceso-personal-data.md §6 A01).
    @Bean
    public RouterFunction<ServerResponse> authRoute() {
        return GatewayRouterFunctions.route("auth")
                .route(path("/api/auth/**").and(path("/api/auth/interno/**").negate()),
                        HandlerFunctions.http("http://auth-service:8081"))
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
                .filter(internalTokenIssuerFilter)
                .filter(stripCookieFilter)
                .build();
    }

    // /api/pedidos/**  →  ms-pedidos:8083/pedidos/**
    //
    // /api/pedidos/interno/** se EXCLUYE deliberadamente — mismo motivo que
    // authRoute(): sin esta exclusión, cualquier usuario autenticado podría
    // leer el historial de pedidos de OTRO usuario editando el email en la
    // URL (ver arco-acceso-personal-data.md §6 A01).
    @Bean
    public RouterFunction<ServerResponse> pedidosRoute() {
        return GatewayRouterFunctions.route("pedidos")
                .route(path("/api/pedidos/**").and(path("/api/pedidos/interno/**").negate()),
                        HandlerFunctions.http("http://ms-pedidos:8083"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .filter(internalTokenIssuerFilter)
                .filter(stripCookieFilter)
                .build();
    }

    // /api/envios/**  →  ms-envios:8084/envios/**
    //
    // /api/envios/interno/** se EXCLUYE deliberadamente — mismo motivo que
    // pedidosRoute() (ver arco-acceso-personal-data.md §6 A01).
    @Bean
    public RouterFunction<ServerResponse> enviosRoute() {
        return GatewayRouterFunctions.route("envios")
                .route(path("/api/envios/**").and(path("/api/envios/interno/**").negate()),
                        HandlerFunctions.http("http://ms-envios:8084"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .filter(internalTokenIssuerFilter)
                .filter(stripCookieFilter)
                .build();
    }

    // Ruta para la Saga — apunta al mismo ms-pedidos, protegida con JWT
    @Bean
    public RouterFunction<ServerResponse> sagasRoute() {
        return GatewayRouterFunctions.route("sagas")
                .route(path("/api/sagas/**"), HandlerFunctions.http("http://ms-pedidos:8083"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .filter(internalTokenIssuerFilter)
                .filter(stripCookieFilter)
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
                .filter(stripCookieFilter)
                .build();
    }

    /**
     * GET /api/session — como /api/dashboard, atendido directamente por
     * SessionController en el gateway (WebHub/BFF), sin llamada a auth-service.
     * A diferencia de las rutas de arriba, aquí NO se aplica stripCookieFilter:
     * SessionController necesita leer la cookie sl_jwt para extraer email/role.
     */
    @Bean
    public RouterFunction<ServerResponse> sessionRoute() {
        return GatewayRouterFunctions.route("session")
                .route(path("/api/session/**"), HandlerFunctions.http("http://localhost:8080"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    /**
     * GET /api/usuarios/me/datos — como /api/session, atendido directamente
     * por UsuarioDatosController en el gateway (WebHub/BFF). Igual que
     * sessionRoute, NO se aplica stripCookieFilter: el controller necesita
     * leer la cookie sl_jwt para derivar la identidad del llamador — nunca
     * de un parámetro de la petición (arco-acceso-personal-data.md §3.1).
     */
    @Bean
    public RouterFunction<ServerResponse> usuariosRoute() {
        return GatewayRouterFunctions.route("usuarios")
                .route(path("/api/usuarios/**"), HandlerFunctions.http("http://localhost:8080"))
                .filter(stripPrefix(1))
                .filter(authFilter)
                .build();
    }

    // /api/pagos/interno/** se EXCLUYE deliberadamente — mismo motivo que
    // pedidosRoute()/enviosRoute()/authRoute(): sin esta exclusión, cualquier
    // usuario autenticado podría anonimizar los pagos de OTRO usuario
    // editando el email en la URL de PUT /api/pagos/interno/por-email/
    // {email}/anonimizar (derecho de cancelación ARCO+ — arco-cancelacion-
    // oposicion.md §6.4/§7 A01, el detalle de implementación más crítico de
    // todo ese diseño: ms-pagos nunca tuvo un endpoint /interno/** antes,
    // así que pagosRoute() nunca había necesitado esta exclusión hasta ahora).
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> pagosRoute() {
        return GatewayRouterFunctions.route("pagos")
                .route(path("/api/pagos/**").and(path("/api/pagos/interno/**").negate()),
                        HandlerFunctions.http("http://ms-pagos:8086"))
                .filter(stripPrefix(1))
                .filter(authFilter)  // JWT requerido para /api/pagos/crear y /api/pagos/{id}
                .filter(internalTokenIssuerFilter)
                .filter(stripCookieFilter)
                .build();
    }

    // IMPORTANTE: El webhook de Flow va por una ruta separada SIN authFilter
    // Flow llama al webhook sin JWT — se excluye del filtro
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> webhookFlowRoute() {
        return GatewayRouterFunctions.route("webhook-flow")
                .route(path("/api/pagos/webhook/flow"), HandlerFunctions.http("http://ms-pagos:8086"))
                .filter(stripPrefix(1))
                // Sin authFilter — Flow no puede enviar JWT
                .build();
    }
}
