package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.EmailYaRegistradoException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.UsuarioNoEncontradoException;
import com.ecommerce.auth.security.InternalAuthFilter;
import com.ecommerce.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired private ObjectMapper objectMapper;
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

    @Test
    void rectificarEmail_credencialesValidas_retorna200ConTokenNuevo() throws Exception {

        // ARRANGE
        RectificacionInternaDTO dto = new RectificacionInternaDTO(
                "correcta@pyme.cl", "ROLE_USER", LocalDateTime.of(2026, 1, 15, 9, 0), "jwt-nuevo");
        when(authService.rectificarEmail(eq("vieja@pyme.cl"), any())).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/vieja@pyme.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("correcta@pyme.cl"))
                .andExpect(jsonPath("$.token").value("jwt-nuevo"));
    }

    @Test
    void rectificarEmail_emailNuevoMalformado_retorna400YNoLlamaAlService() throws Exception {

        // ARRANGE — violación de @Email en RectificarEmailInternoRequest

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/vieja@pyme.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "no-es-un-email", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(authService, org.mockito.Mockito.never()).rectificarEmail(any(), any());
    }

    @Test
    void rectificarEmail_passwordActualIncorrecta_retorna401ConMensajeEspecifico() throws Exception {

        // ARRANGE — abuso crítico: no debe confundirse con "Credenciales inválidas"
        // (mensaje distinto de login, ver diseño §4.1/§6 A09)
        when(authService.rectificarEmail(eq("vieja@pyme.cl"), any()))
                .thenThrow(new InvalidCredentialsException("Contraseña actual incorrecta"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/vieja@pyme.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "correcta@pyme.cl", "passwordActual", "incorrecta"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Contraseña actual incorrecta"));
    }

    @Test
    void rectificarEmail_emailNuevoYaRegistrado_retorna409() throws Exception {

        // ARRANGE
        when(authService.rectificarEmail(eq("vieja@pyme.cl"), any()))
                .thenThrow(new EmailYaRegistradoException("Este email ya está registrado"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/vieja@pyme.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "otra-cuenta@pyme.cl", "passwordActual", "Password123!"))));

        // ASSERT
        respuesta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Este email ya está registrado"));
    }

    @Test
    void rectificarEmail_cuentaYaNoExiste_retorna404() throws Exception {

        // ARRANGE — edge case: JWT válido pero la cuenta ya no existe
        when(authService.rectificarEmail(eq("borrada@pyme.cl"), any()))
                .thenThrow(new UsuarioNoEncontradoException("Usuario no encontrado"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/borrada@pyme.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "emailNuevo", "nueva@pyme.cl", "passwordActual", "cualquiera"))));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }
}
