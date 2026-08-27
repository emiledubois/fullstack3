package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    }
)
@TestPropertySource(properties = "cookie.secure=true")
class AuthControllerTest {

    @Autowired private MockMvc     mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AuthService  authService;

    @Test
    void login_credencialesValidas_retorna200ConCookieYSinTokenEnElBody() throws Exception {

        // ARRANGE
        Instant expiresAt = Instant.parse("2026-08-27T14:00:00Z");
        when(authService.login(any())).thenReturn(
                new LoginResult("jwt-secreto", "user@pyme.cl", "ROLE_USER", expiresAt, 86400L));

        // ACT
        var respuesta = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@pyme.cl", "password", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@pyme.cl"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("sl_jwt=jwt-secreto"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/"),
                                org.hamcrest.Matchers.containsString("Secure"))));

        // El JWT no debe aparecer en ningún campo del body de la respuesta
        respuesta.andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("jwt-secreto"))));
    }

    @Test
    void login_credencialesInvalidas_retorna401SinSetCookie() throws Exception {

        // ARRANGE
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Credenciales inválidas"));

        // ACT
        var respuesta = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@pyme.cl", "password", "incorrecta"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    @Test
    void login_camposEnBlanco_retorna400() throws Exception {

        // ARRANGE
        // email y password vacíos — violan @NotBlank de LoginRequest

        // ACT
        var respuesta = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "", "password", ""))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        verify(authService, never()).login(any());
    }

    @Test
    void logout_conCookieValida_retorna200YExpiraLaCookie() throws Exception {

        // ARRANGE
        doNothing().when(authService).logout("jwt-existente");

        // ACT
        var respuesta = mockMvc.perform(post("/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("sl_jwt", "jwt-existente")));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(authService).logout("jwt-existente");
    }

    @Test
    void logout_sinCookie_retorna200IgualYNoFalla() throws Exception {

        // ARRANGE — no se envía cookie sl_jwt en la petición
        doNothing().when(authService).logout(null);

        // ACT
        var respuesta = mockMvc.perform(post("/auth/logout"));

        // ASSERT — idempotente: sin sesión también responde 200
        respuesta.andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(authService).logout(null);
    }
}
