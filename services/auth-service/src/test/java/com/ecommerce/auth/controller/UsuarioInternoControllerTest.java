package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.security.InternalAuthFilter;
import com.ecommerce.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// InternalAuthFilter se excluye de este slice test: su lógica (allowlist,
// firma HMAC, TTL) ya se prueba exhaustivamente en InternalAuthFilterTest;
// aquí se prueba solo el comportamiento del controller.
@WebMvcTest(
    value = UsuarioInternoController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InternalAuthFilter.class)
)
class UsuarioInternoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private AuthService authService;

    @Test
    void getUsuario_emailExiste_retorna200SinCampoPassword() throws Exception {

        // ARRANGE
        UsuarioInternoDTO dto = new UsuarioInternoDTO(
                "dueña@pyme.cl", "ROLE_USER", LocalDateTime.of(2026, 1, 15, 9, 0));
        when(authService.buscarUsuarioInterno("dueña@pyme.cl")).thenReturn(Optional.of(dto));

        // ACT
        var respuesta = mockMvc.perform(get("/auth/interno/usuarios/dueña@pyme.cl"));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dueña@pyme.cl"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.contraseña").doesNotExist());
    }

    @Test
    void getUsuario_emailNoExiste_retorna404() throws Exception {

        // ARRANGE
        when(authService.buscarUsuarioInterno("nadie@pyme.cl")).thenReturn(Optional.empty());

        // ACT
        var respuesta = mockMvc.perform(get("/auth/interno/usuarios/nadie@pyme.cl"));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }
}
