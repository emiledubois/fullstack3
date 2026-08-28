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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// RectificacionController se ejercita vía MockMvc en modo standalone (no
// @WebMvcTest completo: sin filtros/seguridad, igual que UsuarioDatosController
// no los necesita, ver UsuarioDatosControllerTest) — a diferencia de ese
// controller, este SÍ tiene un @RequestBody validado con Bean Validation, así
// que aquí conviene pasar por el pipeline real de deserialización+@Valid en
// vez de invocar el método Java directamente.
@ExtendWith(MockitoExtension.class)
class RectificacionControllerTest {

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

        RectificacionController controller = new RectificacionController(
                jwtUtil, baseUrl(authServer), WebClient.builder(), signer);
        ReflectionTestUtils.setField(controller, "cookieSecure", true);

        // ByteArrayHttpMessageConverter es necesario además del de Jackson:
        // el catch de WebClientResponseException del controller devuelve un
        // ResponseEntity<byte[]> (repropaga el body de auth-service tal
        // cual) — sin este converter, Jackson trataría el byte[] como un
        // valor a serializar (base64) en vez de escribirlo tal cual.
        ObjectMapper respuestaMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(respuestaMapper))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        authServer.shutdown();
    }

    @Test
    void rectificarEmail_credencialesValidasYEmailNuevoDisponible_retorna200ConCookieRotada() throws Exception {

        // ARRANGE
        cookieValidaParaEmail("vieja@pyme.cl");
        when(jwtUtil.getRemainingSeconds("jwt-nuevo")).thenReturn(86400L);
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"email\":\"correcta@pyme.cl\",\"role\":\"ROLE_USER\","
                        + "\"cuentaCreadaEn\":\"2026-01-15T09:00:00\",\"token\":\"jwt-nuevo\"}"));

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("correcta@pyme.cl"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("sl_jwt=jwt-nuevo"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Lax"))));

        // El JWT nunca debe aparecer en el body de la respuesta pública
        respuesta.andExpect(content().string(org.hamcrest.Matchers.not(containsString("jwt-nuevo"))));
    }

    @Test
    void rectificarEmail_sinCookie_retorna401YNoLlamaAAuthService() throws Exception {

        // ARRANGE — sin cookie sl_jwt

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void rectificarEmail_cookieInvalida_retorna401() throws Exception {

        // ARRANGE
        when(jwtUtil.isValid("token-invalido")).thenReturn(false);

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-invalido"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void rectificarEmail_emailNuevoMalformado_retorna400YNoLlamaAAuthService() throws Exception {

        // ARRANGE — violación de @Email en RectificarEmailRequest (Bean
        // Validation real vía el pipeline de MockMvc, no una llamada Java
        // directa). Spring rechaza el body ANTES de que el método del
        // controller se ejecute, así que ni siquiera se llega a leer la
        // cookie — no se estuban jwtUtil aquí a propósito.

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "no-es-un-email", "passwordActual", "Password123!"))));

        // ASSERT — criterio de aceptación 4: ninguna llamada interna se hizo
        respuesta.andExpect(status().isBadRequest());
        assertEquals(0, authServer.getRequestCount());
    }

    @Test
    void rectificarEmail_passwordActualIncorrecta_retorna401RepropagadoSinSetCookie() throws Exception {

        // ARRANGE — auth-service ya determinó y logueó el rechazo; el
        // gateway solo repropaga status+mensaje (§7 A09: no duplicar auditoría)
        cookieValidaParaEmail("vieja@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"Contraseña actual incorrecta\"}"));

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "incorrecta"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Contraseña actual incorrecta"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void rectificarEmail_emailNuevoYaRegistrado_retorna409Repropagado() throws Exception {

        // ARRANGE
        cookieValidaParaEmail("vieja@pyme.cl");
        authServer.enqueue(new MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"Este email ya está registrado\"}"));

        // ACT
        var respuesta = mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "otra-cuenta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Este email ya está registrado"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void rectificarEmail_identidadSiempreDerivadaDeLaCookie_noDelBody() throws Exception {

        // ARRANGE — criterio de aceptación 5 (IDOR crítico): aunque el body
        // llegara con un campo extra (ignorado por Jackson, ver
        // RectificarEmailRequestTest para la garantía estructural), el
        // {email} usado en la llamada interna es SIEMPRE el de la cookie
        cookieValidaParaEmail("vieja@pyme.cl");
        when(jwtUtil.getRemainingSeconds(org.mockito.ArgumentMatchers.any())).thenReturn(86400L);
        authServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"email\":\"correcta@pyme.cl\",\"role\":\"ROLE_USER\",\"token\":\"jwt-nuevo\"}"));

        // ACT
        mockMvc.perform(put("/usuarios/me/email")
                .cookie(new Cookie(COOKIE_NAME, "token-actual"))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT — timeout explícito: si por algún motivo la petición nunca
        // llega a authServer, esto debe fallar rápido, no colgar la suite
        var peticionInterna = authServer.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
        // WebClient codifica el path variable (arroba -> %40) — lo relevante
        // es que sea "vieja" (de la cookie) y NUNCA "correcta"/otro valor.
        assertEquals("/auth/interno/usuarios/vieja%40pyme.cl", peticionInterna.getPath());
    }

    private void cookieValidaParaEmail(String email) {
        when(jwtUtil.isValid("token-actual")).thenReturn(true);
        when(jwtUtil.extractEmail("token-actual")).thenReturn(email);
    }

    private static String baseUrl(MockWebServer server) {
        return "http://localhost:" + server.getPort();
    }
}
