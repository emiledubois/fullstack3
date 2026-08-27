package com.ecommerce.api.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de extremo a extremo del enrutamiento (no de la lógica de negocio):
 * los paths /interno/** de auth/pedidos/envios deben ser un 404 del propio
 * gateway (ninguna ruta los matchea) — el criterio de aceptación 5b/c/d del
 * diseño ARCO+. No requiere que auth-service/ms-pedidos/ms-envios estén
 * levantados: si la ruta no matchea, la petición nunca llega a intentar
 * proxear nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayConfigTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getPedidosInternoPorEmail_viaGateway_retorna404SinImportarQueNoHayaCookie() {

        // ARRANGE — ni siquiera se envía cookie: si la ruta no matchea,
        // AuthFilter no llega a ejecutarse en absoluto
        String url = "http://localhost:" + port + "/api/pedidos/interno/por-email/victima@pyme.cl";

        // ACT
        ResponseEntity<String> respuesta = restTemplate.getForEntity(url, String.class);

        // ASSERT
        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void getAuthInternoUsuarios_viaGateway_retorna404() {

        // ARRANGE
        String url = "http://localhost:" + port + "/api/auth/interno/usuarios/victima@pyme.cl";

        // ACT
        ResponseEntity<String> respuesta = restTemplate.getForEntity(url, String.class);

        // ASSERT
        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void getEnviosInternoPorPedidos_viaGateway_retorna404() {

        // ARRANGE
        String url = "http://localhost:" + port + "/api/envios/interno/por-pedidos?pedidoIds=1,2,3";

        // ACT
        ResponseEntity<String> respuesta = restTemplate.getForEntity(url, String.class);

        // ASSERT
        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void getPedidos_regresion_siSigueProtegidoPorAuthFilterYNoSeConfundeCon404() {

        // ARRANGE — regresión (criterio 11): /api/pedidos (sin /interno) debe
        // seguir siendo una ruta válida protegida por JWT — sin cookie,
        // AuthFilter responde 401, NUNCA un 404 de enrutamiento no matcheado
        String url = "http://localhost:" + port + "/api/pedidos";

        // ACT
        ResponseEntity<String> respuesta = restTemplate.getForEntity(url, String.class);

        // ASSERT
        assertEquals(401, respuesta.getStatusCode().value());
    }

    @Test
    void postAuthLogin_regresion_siSigueSiendoRutaPublicaMatcheada() {

        // ARRANGE — regresión (criterio 11): /api/auth/login (sin /interno)
        // debe seguir matcheando authRoute() — el gateway debe intentar
        // proxear (aunque auth-service no esté levantado en este test, la
        // respuesta nunca es un 404 de "ruta no encontrada" del gateway)
        String url = "http://localhost:" + port + "/api/auth/login";

        // ACT
        ResponseEntity<String> respuesta = restTemplate.postForEntity(url, null, String.class);

        // ASSERT
        assertNotEquals(404, respuesta.getStatusCode().value());
    }
}
