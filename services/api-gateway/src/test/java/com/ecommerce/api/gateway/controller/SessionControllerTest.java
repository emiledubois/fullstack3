package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = SessionController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    }
)
class SessionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private JwtUtil jwtUtil;

    @Test
    void getSession_cookieValida_retorna200ConEmailYRole() throws Exception {

        // ARRANGE
        when(jwtUtil.isValid("token-valido")).thenReturn(true);
        when(jwtUtil.extractEmail("token-valido")).thenReturn("user@pyme.cl");
        when(jwtUtil.extractRole("token-valido")).thenReturn("ROLE_USER");

        // ACT
        var respuesta = mockMvc.perform(get("/session")
                .cookie(new Cookie("sl_jwt", "token-valido")));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@pyme.cl"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void getSession_sinCookie_retorna401() throws Exception {

        // ARRANGE — no se envía ninguna cookie

        // ACT
        var respuesta = mockMvc.perform(get("/session"));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
    }

    @Test
    void getSession_cookieInvalida_retorna401() throws Exception {

        // ARRANGE
        when(jwtUtil.isValid("token-expirado")).thenReturn(false);

        // ACT
        var respuesta = mockMvc.perform(get("/session")
                .cookie(new Cookie("sl_jwt", "token-expirado")));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized());
    }
}
