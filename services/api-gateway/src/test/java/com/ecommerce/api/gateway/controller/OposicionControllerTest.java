package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.Cookie;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// OposicionController se ejercita vía MockMvc en modo standalone, mismo
// patrón que RectificacionControllerTest — la única llamada WebClient
// (auth-service) habla con MockWebServer local.
@ExtendWith(MockitoExtension.class)
class OposicionControllerTest {

    private static final String COOKIE_NAME = "sl_jwt";
    private static final String SECRET = "shared-internal-secret";

    @Mock private JwtUtil jwtUtil;

    private MockWebServer authServer;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        authServer = new MockWebServer();
        authServer.start();

        InternalTokenSigner signer = new InternalTokenSigner();
        ReflectionTestUtils.setField(signer, "secret", SECRET);

        OposicionController controller = new OposicionController(
                jwtUtil, baseUrl(authServer), WebClient.builder(), signer);

        // ObjectMapper con JavaTimeModule para el conversor de SALIDA de
        // MockMvc — la respuesta pública incluye oposicionRegistradaEn
        // (LocalDateTime), igual que RectificacionResponseDTO en
        // RectificacionControllerTest.
        ObjectMapper respuestaMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(respuestaMapper))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        authServer.shutdown();
    }

    @Test
    void registrarOposicion_oponerseTrue_retorna200ConFlagYTimestamp() throws Exception {

        // ARRANGE — criterio de aceptación 16
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"oposicionProcesamiento\":true,\"oposicionRegistradaEn\":\"2026-08-28T10:00:00\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.oposicionProcesamiento").value(true))
                .andExpect(jsonPath("$.oposicionRegistradaEn").value("2026-08-28T10:00:00"));

        // Identidad SIEMPRE derivada de la cookie, nunca de un parámetro del
        // cliente (criterio de aceptación 20 — anti-IDOR)
        var peticionInterna = authServer.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("/auth/interno/usuarios/due%C3%B1a%40pyme.cl/oposicion", peticionInterna.getPath());
    }

    @Test
    void registrarOposicion_oponerseFalse_retorna200FlagFalso() throws Exception {

        // ARRANGE — criterio de aceptación 17: no es un ratchet de un solo sentido
        cookieValidaParaEmail("dueña@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"oposicionProcesamiento\":false,\"oposicionRegistradaEn\":\"2026-08-28T11:00:00\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", false))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.oposicionProcesamiento").value(false));
    }

    @Test
    void registrarOposicion_oponerseFaltante_retorna400YNoLlamaAAuthService() throws Exception {

        // ARRANGE — criterio de aceptación 18: violación de @NotNull

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void registrarOposicion_sinCookie_retorna401YNoLlamaAAuthService() throws Exception {

        // ARRANGE — criterio de aceptación 19

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void registrarOposicion_cookieInvalida_retorna401() throws Exception {

        // ARRANGE
        when(jwtUtil.isValid("token-invalido")).thenReturn(false);

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .cookie(new Cookie(COOKIE_NAME, "token-invalido"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void registrarOposicion_cuentaYaNoExiste_retorna404Repropagado() throws Exception {

        // ARRANGE — cookie válida pero cuenta ya cancelada/no resuelve
        cookieValidaParaEmail("borrada@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"Usuario no encontrado\"}"));

        // ACT
        var respuesta = mockMvc.perform(post("/usuarios/me/oposicion")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }

    private void cookieValidaParaEmail(String email) {
        when(jwtUtil.isValid("token-actual")).thenReturn(true);
        when(jwtUtil.extractEmail("token-actual")).thenReturn(email);
    }

    private static String baseUrl(MockWebServer server) {
        return "http://localhost:" + server.getPort();
    }
}
