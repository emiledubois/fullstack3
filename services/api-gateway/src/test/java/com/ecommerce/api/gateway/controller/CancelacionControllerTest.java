package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// CancelacionController se ejercita vía MockMvc en modo standalone, igual que
// RectificacionControllerTest — las cuatro llamadas WebClient (auth/pedidos/
// envíos/pagos) hablan con MockWebServer local, sin mockear la API fluida de
// WebClient (ver UsuarioDatosControllerTest para el mismo razonamiento).
@ExtendWith(MockitoExtension.class)
class CancelacionControllerTest {

    private static final String COOKIE_NAME = "sl_jwt";
    private static final String SECRET = "shared-internal-secret";

    @Mock private JwtUtil jwtUtil;

    private MockWebServer authServer;
    private MockWebServer pedidosServer;
    private MockWebServer enviosServer;
    private MockWebServer pagosServer;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        authServer = new MockWebServer();
        pedidosServer = new MockWebServer();
        enviosServer = new MockWebServer();
        pagosServer = new MockWebServer();
        authServer.start();
        pedidosServer.start();
        enviosServer.start();
        pagosServer.start();

        InternalTokenSigner signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", SECRET);

        CancelacionController controller = new CancelacionController(
                jwtUtil,
                baseUrl(authServer), baseUrl(pedidosServer), baseUrl(enviosServer), baseUrl(pagosServer),
                WebClient.builder(), signer);
        ReflectionTestUtils.setField(controller, "cookieSecure", true);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        authServer.shutdown();
        pedidosServer.shutdown();
        enviosServer.shutdown();
        pagosServer.shutdown();
        MDC.clear();
    }

    @Test
    void cancelarCuenta_sinPedidos_retorna200ConCookieLimpiadaYCuentaCancelada() throws Exception {

        // ARRANGE — criterio de aceptación 1
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":7,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        pagosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":7,\"email\":\"usuario-eliminado-7@smartlogix.invalid\",\"status\":\"CANCELADA\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("sl_jwt="),
                        containsString("Max-Age=0"))));
        assertEquals(0, enviosServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_confirmacionIncorrecta_retorna400YNoLlamaANingunServicio() throws Exception {

        // ARRANGE — criterio de aceptación 2: sin cambio de estado, ni
        // siquiera un intento de "iniciar". Solo se stubea isValid (no
        // extractEmail): la confirmación inválida corta el flujo antes de
        // que el controller necesite derivar el email de la cookie.
        when(jwtUtil.isValid("token-actual")).thenReturn(true);

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "eliminar_mi_cuenta"))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        assertEquals(0, authServer.getRequestCount());
        assertEquals(0, pedidosServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_passwordActualEnBlanco_retorna400YNoLlamaANingunServicio() throws Exception {

        // ARRANGE — violación de @NotBlank vía Bean Validation real (MockMvc)

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_passwordActualIncorrecta_retorna401RepropagadoYNoLlamaAPedidos() throws Exception {

        // ARRANGE — criterio de aceptación 3
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"Contraseña actual incorrecta\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "incorrecta", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Contraseña actual incorrecta"));
        assertEquals(0, pedidosServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_sinCookie_retorna401YNoLlamaANingunServicio() throws Exception {

        // ARRANGE — criterio de aceptación 13

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_pedidoPendiente_retorna409ConIdBloqueanteYRevierteSinTocarPedidosNiPagos() throws Exception {

        // ARRANGE — criterio de aceptación 4
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":8,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":12,\"status\":\"PENDIENTE\"}]"));
        enviosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":8,\"email\":\"dueña@pyme.cl\",\"status\":\"ACTIVA\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.pedidosBloqueantes[0]").value(12));
        assertEquals(1, pedidosServer.getRequestCount()); // solo la lectura, ningún PUT de anonimizar
        assertEquals(0, pagosServer.getRequestCount());

        // Verifica que se llamó a revertir, no a finalizar
        RecordedRequest primeraLlamada = authServer.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest segundaLlamada = authServer.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("PUT", primeraLlamada.getMethod());
        assertEquals("PUT", segundaLlamada.getMethod());
        org.assertj.core.api.Assertions.assertThat(segundaLlamada.getPath()).contains("/cancelacion/revertir");
    }

    @Test
    void cancelarCuenta_pedidoEntregadoYEnvioEntregado_retorna200() throws Exception {

        // ARRANGE — criterio de aceptación 5: el allowlist no sobre-bloquea
        // un pedido genuinamente completado
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":9,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":42,\"status\":\"ENTREGADO\"}]"));
        enviosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":17,\"pedidoId\":42,\"status\":\"ENTREGADO\"}]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":1}"));
        pagosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":1}"));
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":9,\"status\":\"CANCELADA\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    void cancelarCuenta_pedidoPagoAnuladoSinEnvio_retorna200() throws Exception {

        // ARRANGE — criterio de aceptación 6: pago fallido terminal sin
        // ningún envío asociado (Saga nunca se disparó) no bloquea
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":10,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":55,\"status\":\"PAGO_ANULADO\"}]"));
        enviosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":1}"));
        pagosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":10,\"status\":\"CANCELADA\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    void cancelarCuenta_pagosCaidoTrasAgotarReintentos_retorna202YNoFinaliza() throws Exception {

        // ARRANGE — criterio de aceptación 9 (retry-to-completion, primera
        // mitad): ms-pagos caído — 4 intentos (inicial + 3 reintentos) fallan
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":11,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":1}"));
        for (int i = 0; i < 4; i++) {
            pagosServer.enqueue(new MockResponse().setResponseCode(500));
        }

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PARCIAL"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertEquals(4, pagosServer.getRequestCount());
        // Solo la llamada de "iniciar" a auth-service — "finalizar" nunca se llama
        assertEquals(1, authServer.getRequestCount());
    }

    @Test
    void cancelarCuenta_finalizarFallaTrasAnonimizarConExito_retorna202SinCookie() throws Exception {

        // ARRANGE — la anonimización ya tuvo éxito en pedidos/pagos; el
        // último salto a auth-service (finalizar) falla transitoriamente.
        // Debe degradar a 202 PARCIAL (sin cookie de sesión limpiada) en vez
        // de propagar la excepción del WebClient sin manejar.
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":13,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        pagosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        authServer.enqueue(new MockResponse().setResponseCode(500));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PARCIAL"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertEquals(2, authServer.getRequestCount()); // iniciar + finalizar (fallido)
    }

    @Test
    void cancelarCuenta_conCorrelationIdEnMdc_loPropagaATodasLasLlamadasSalientes() throws Exception {

        // ARRANGE — MDC.put simula lo que CorrelationIdFilter (registrado
        // globalmente por Spring Boot, no presente en este MockMvc standalone)
        // ya habría fijado en el hilo del servlet antes de llegar al controller.
        MDC.put("correlationId", "cid-happy-path");
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":20,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        pagosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":0}"));
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":20,\"status\":\"CANCELADA\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isOk());
        assertEquals("cid-happy-path", authServer.takeRequest(2, TimeUnit.SECONDS).getHeader("X-Correlation-Id"));
        assertEquals("cid-happy-path", pedidosServer.takeRequest(2, TimeUnit.SECONDS).getHeader("X-Correlation-Id"));
        assertEquals("cid-happy-path", pedidosServer.takeRequest(2, TimeUnit.SECONDS).getHeader("X-Correlation-Id"));
        assertEquals("cid-happy-path", pagosServer.takeRequest(2, TimeUnit.SECONDS).getHeader("X-Correlation-Id"));
        assertEquals("cid-happy-path", authServer.takeRequest(2, TimeUnit.SECONDS).getHeader("X-Correlation-Id"));
    }

    @Test
    void cancelarCuenta_reintentosDeAnonimizacion_todosLosIntentosLlevanElMismoCorrelationId() throws Exception {

        // ARRANGE — hazard identificado en observability-correlation-ids.md
        // §5.3/criterio de aceptación 4: Retry.backoff() resuscribe en
        // Schedulers.parallel(), un hilo distinto del hilo del servlet donde
        // se fijó el MDC. El correlationId debe capturarse en una variable
        // local y hornearse como cabecera literal ANTES del reintento — no
        // volver a leer MDC.get() dentro del operador reactivo — para que
        // los 4 intentos (inicial + 3 reintentos) lleven el MISMO valor.
        MDC.put("correlationId", "cid-retry-1");
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":21,\"email\":\"dueña@pyme.cl\",\"status\":\"CANCELACION_EN_PROGRESO\"}"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("[]"));
        pedidosServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"cantidadAnonimizada\":1}"));
        for (int i = 0; i < 4; i++) {
            pagosServer.enqueue(new MockResponse().setResponseCode(500));
        }

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/cancelacion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isAccepted());
        assertEquals(4, pagosServer.getRequestCount());
        for (int i = 0; i < 4; i++) {
            RecordedRequest intento = pagosServer.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("cid-retry-1", intento.getHeader("X-Correlation-Id"),
                    "intento #" + (i + 1) + " perdió el correlationId");
        }
    }

    private void cookieValidaParaEmail(String email) {
        when(jwtUtil.isValid("token-actual")).thenReturn(true);
        when(jwtUtil.extractEmail("token-actual")).thenReturn(email);
    }

    private static String baseUrl(MockWebServer server) {
        return "http://localhost:" + server.getPort();
    }
}
