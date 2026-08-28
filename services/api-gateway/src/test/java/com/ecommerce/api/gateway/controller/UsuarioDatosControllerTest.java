package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.ExportacionDatosDTO;
import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// UsuarioDatosController se instancia directamente (no @WebMvcTest): las tres
// llamadas WebClient hablan con MockWebServer local en vez de mockear la API
// fluida de WebClient (get().uri().retrieve().bodyToMono(...)), que con
// deep-stubs se vuelve frágil. JwtUtil sí se mockea — su propio contrato ya
// se prueba en JwtUtilTest/AuthFilterTest.
@ExtendWith(MockitoExtension.class)
class UsuarioDatosControllerTest {

    private static final String COOKIE_NAME = "sl_jwt";
    private static final String SECRET = "shared-internal-secret";

    @Mock private JwtUtil jwtUtil;

    private MockWebServer authServer;
    private MockWebServer pedidosServer;
    private MockWebServer enviosServer;

    private UsuarioDatosController controller;

    @BeforeEach
    void setUp() throws IOException {
        authServer = new MockWebServer();
        pedidosServer = new MockWebServer();
        enviosServer = new MockWebServer();
        authServer.start();
        pedidosServer.start();
        enviosServer.start();

        InternalTokenSigner signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", SECRET);

        controller = new UsuarioDatosController(
            jwtUtil,
            baseUrl(authServer), baseUrl(pedidosServer), baseUrl(enviosServer),
            WebClient.builder(), signer);
    }

    @AfterEach
    void tearDown() throws IOException {
        authServer.shutdown();
        pedidosServer.shutdown();
        enviosServer.shutdown();
    }

    @Test
    void getMisDatos_sinCookie_retorna401YNoLlamaANingunServicio() {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest();

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        assertEquals(0, authServer.getRequestCount());
        assertEquals(0, pedidosServer.getRequestCount());
    }

    @Test
    void getMisDatos_cookieInvalida_retorna401() {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, "token-invalido"));
        when(jwtUtil.isValid("token-invalido")).thenReturn(false);

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
    }

    @Test
    void getMisDatos_usuarioConPedidosYEnvios_retorna200ConTodoAgregadoYEstadoOK() throws Exception {

        // ARRANGE
        MockHttpServletRequest request = cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"dueña@pyme.cl\",\"role\":\"ROLE_USER\",\"cuentaCreadaEn\":\"2026-01-15T09:00:00\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"id\":42,\"clienteNombre\":\"Comercial Andina Ltda.\",\"total\":15990.0,"
                + "\"status\":\"ENTREGADO\",\"tipoPedido\":\"NACIONAL\",\"destino\":\"Valparaíso\"}]"));
        enviosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"id\":17,\"pedidoId\":42,\"status\":\"ENTREGADO\",\"tipoEnvio\":\"TERRESTRE\"}]"));

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = (com.ecommerce.api.gateway.dto.UsuarioDatosDTO) respuesta.getBody();
        assertNotNull(body);
        assertEquals("OK", body.getEstadoAgregacion());
        assertEquals("dueña@pyme.cl", body.getCuenta().getEmail());
        assertEquals(1, body.getPedidos().size());
        assertEquals(1, body.getEnvios().size());
        assertEquals(42L, body.getEnvios().get(0).getPedidoId());

        // La llamada a ms-envios debe usar los pedidoIds de ms-pedidos
        var enviosRequest = enviosServer.takeRequest();
        assertTrue(enviosRequest.getPath().contains("pedidoIds=42"));
    }

    @Test
    void getMisDatos_usuarioSinPedidos_retorna200ConListasVaciasYEstadoOKSinLlamarAEnvios() throws Exception {

        // ARRANGE — criterio de aceptación 2: vacío legítimo, distinto de una caída
        MockHttpServletRequest request = cookieValidaParaEmail("nuevo@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"nuevo@pyme.cl\",\"role\":\"ROLE_USER\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[]"));

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = (com.ecommerce.api.gateway.dto.UsuarioDatosDTO) respuesta.getBody();
        assertEquals("OK", body.getEstadoAgregacion());
        assertTrue(body.getPedidos().isEmpty());
        assertTrue(body.getEnvios().isEmpty());
        assertEquals(0, enviosServer.getRequestCount());
    }

    @Test
    void getMisDatos_msPedidosCaido_retorna200ConPedidosVaciosYEstadoNuncaOK() throws Exception {

        // ARRANGE — criterio de aceptación 7: caída != "sin datos"
        MockHttpServletRequest request = cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"dueña@pyme.cl\",\"role\":\"ROLE_USER\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(500));

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = (com.ecommerce.api.gateway.dto.UsuarioDatosDTO) respuesta.getBody();
        assertTrue(body.getPedidos().isEmpty());
        assertTrue(body.getEnvios().isEmpty());
        assertNotEquals("OK", body.getEstadoAgregacion());
        assertEquals(0, enviosServer.getRequestCount());
    }

    @Test
    void getMisDatos_cuentaNoEncontrada_retorna200ConCuentaNulaYEstadoOK() throws Exception {

        // ARRANGE — edge case documentado en el diseño §3.1: JWT válido pero
        // el email ya no resuelve a una cuenta (no es una caída del servicio)
        MockHttpServletRequest request = cookieValidaParaEmail("borrado@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(404));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[]"));

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = (com.ecommerce.api.gateway.dto.UsuarioDatosDTO) respuesta.getBody();
        assertNull(body.getCuenta());
        assertEquals("OK", body.getEstadoAgregacion());
    }

    @Test
    void getMisDatos_respuestaSerializada_nuncaContieneLaPalabraPassword() throws Exception {

        // ARRANGE — regresión estática del criterio de aceptación 1/6
        MockHttpServletRequest request = cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"dueña@pyme.cl\",\"role\":\"ROLE_USER\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody("[]"));

        // ACT
        ResponseEntity<?> respuesta = controller.getMisDatos(request);

        // ASSERT
        var body = (com.ecommerce.api.gateway.dto.UsuarioDatosDTO) respuesta.getBody();
        assertNotNull(body.getCuenta());
        assertTrue(java.util.Arrays.stream(body.getCuenta().getClass().getDeclaredFields())
            .noneMatch(f -> f.getName().toLowerCase().contains("password")));
    }

    private MockHttpServletRequest cookieValidaParaEmail(String email) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, "token-valido"));
        when(jwtUtil.isValid("token-valido")).thenReturn(true);
        when(jwtUtil.extractEmail("token-valido")).thenReturn(email);
        return request;
    }

    @Test
    void exportarMisDatos_usuarioConPedidosYEnvios_retorna200ConEnvoltorioYContentDisposition() throws Exception {

        // ARRANGE — criterio de aceptación 13: mismo "datos" que getMisDatos
        // para el mismo usuario/momento
        MockHttpServletRequest request = cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"dueña@pyme.cl\",\"role\":\"ROLE_USER\",\"cuentaCreadaEn\":\"2026-01-15T09:00:00\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"id\":42,\"clienteNombre\":\"Comercial Andina Ltda.\",\"total\":15990.0,"
                + "\"status\":\"ENTREGADO\",\"tipoPedido\":\"NACIONAL\",\"destino\":\"Valparaíso\"}]"));
        enviosServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"id\":17,\"pedidoId\":42,\"status\":\"ENTREGADO\",\"tipoEnvio\":\"TERRESTRE\"}]"));

        // ACT
        ResponseEntity<ExportacionDatosDTO> respuesta = controller.exportarMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        ExportacionDatosDTO body = respuesta.getBody();
        assertNotNull(body);
        assertEquals("1.0", body.getFormatoVersion());
        assertEquals("PORTABILIDAD_ARCO", body.getTipoSolicitud());
        assertEquals("dueña@pyme.cl", body.getTitular());
        assertEquals("OK", body.getDatos().getEstadoAgregacion());
        assertEquals(1, body.getDatos().getPedidos().size());
        assertEquals(1, body.getDatos().getEnvios().size());

        String contentDisposition = respuesta.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.startsWith("attachment; filename=\"smartlogix-mis-datos-"));
        assertTrue(contentDisposition.endsWith(".json\""));
    }

    @Test
    void exportarMisDatos_sinCookie_retorna401YNoLlamaANingunServicio() {

        // ARRANGE
        MockHttpServletRequest request = new MockHttpServletRequest();

        // ACT
        ResponseEntity<ExportacionDatosDTO> respuesta = controller.exportarMisDatos(request);

        // ASSERT — mismo comportamiento que acceso (criterio de aceptación 14)
        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        assertEquals(0, authServer.getRequestCount());
        assertEquals(0, pedidosServer.getRequestCount());
    }

    @Test
    void exportarMisDatos_msPedidosCaido_exportaEstadoParcialNuncaOK() throws Exception {

        // ARRANGE — criterio de aceptación 16: no maquillar una caída como
        // export completo
        MockHttpServletRequest request = cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"email\":\"dueña@pyme.cl\",\"role\":\"ROLE_USER\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(500));

        // ACT
        ResponseEntity<ExportacionDatosDTO> respuesta = controller.exportarMisDatos(request);

        // ASSERT
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotEquals("OK", respuesta.getBody().getDatos().getEstadoAgregacion());
    }

    private static String baseUrl(MockWebServer server) {
        return "http://localhost:" + server.getPort();
    }
}
