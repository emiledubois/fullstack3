package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.CancelacionInternaDTO;
import com.ecommerce.auth.dto.OposicionInternaDTO;
import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.ConfirmacionInvalidaException;
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
                "dueña@pyme.cl", "ROLE_USER", LocalDateTime.of(2026, 1, 15, 9, 0), false, null);
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

    @Test
    void iniciarCancelacion_credencialesValidas_retorna200ConIdYStatusEnProgreso() throws Exception {

        // ARRANGE
        CancelacionInternaDTO dto = new CancelacionInternaDTO(
                7L, "dueña@pyme.cl", "CANCELACION_EN_PROGRESO", LocalDateTime.of(2026, 8, 28, 10, 0), null);
        when(authService.iniciarCancelacion(eq("dueña@pyme.cl"), any())).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("CANCELACION_EN_PROGRESO"));
    }

    @Test
    void iniciarCancelacion_passwordActualEnBlanco_retorna400YNoLlamaAlService() throws Exception {

        // ARRANGE — violación de @NotBlank en CancelacionInternoRequest

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(authService, org.mockito.Mockito.never()).iniciarCancelacion(any(), any());
    }

    @Test
    void iniciarCancelacion_passwordActualIncorrecta_retorna401() throws Exception {

        // ARRANGE
        when(authService.iniciarCancelacion(eq("dueña@pyme.cl"), any()))
                .thenThrow(new InvalidCredentialsException("Contraseña actual incorrecta"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "incorrecta", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Contraseña actual incorrecta"));
    }

    @Test
    void iniciarCancelacion_confirmacionInvalida_retorna400() throws Exception {

        // ARRANGE — defensa en profundidad: auth-service también rechaza,
        // no solo el gateway (diseño §6.2)
        when(authService.iniciarCancelacion(eq("dueña@pyme.cl"), any()))
                .thenThrow(new ConfirmacionInvalidaException("Debe escribir exactamente ELIMINAR_MI_CUENTA para confirmar"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "Password123!", "confirmacion", "eliminar_mi_cuenta"))));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
    }

    @Test
    void iniciarCancelacion_cuentaNoExiste_retorna404() throws Exception {

        // ARRANGE
        when(authService.iniciarCancelacion(eq("borrada@pyme.cl"), any()))
                .thenThrow(new UsuarioNoEncontradoException("Usuario no encontrado"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/borrada@pyme.cl/cancelacion/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "passwordActual", "cualquiera", "confirmacion", "ELIMINAR_MI_CUENTA"))));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }

    @Test
    void revertirCancelacion_cuentaEnProgreso_retorna200ConStatusActiva() throws Exception {

        // ARRANGE
        CancelacionInternaDTO dto = new CancelacionInternaDTO(
                7L, "dueña@pyme.cl", "ACTIVA", null, null);
        when(authService.revertirCancelacion("dueña@pyme.cl")).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/revertir"));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVA"));
    }

    @Test
    void finalizarCancelacion_conIdValido_retorna200ConStatusCancelada() throws Exception {

        // ARRANGE
        CancelacionInternaDTO dto = new CancelacionInternaDTO(
                7L, "usuario-eliminado-7@smartlogix.invalid", "CANCELADA", null, LocalDateTime.now());
        when(authService.finalizarCancelacion(7L)).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/finalizar")
                .param("id", "7"));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void finalizarCancelacion_idNoExiste_retorna404() throws Exception {

        // ARRANGE
        when(authService.finalizarCancelacion(999L))
                .thenThrow(new UsuarioNoEncontradoException("Usuario no encontrado"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/cancelacion/finalizar")
                .param("id", "999"));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }

    @Test
    void registrarOposicion_oponerseTrue_retorna200ConFlagYTimestamp() throws Exception {

        // ARRANGE
        OposicionInternaDTO dto = new OposicionInternaDTO(true, LocalDateTime.of(2026, 8, 28, 10, 0));
        when(authService.registrarOposicion(eq("dueña@pyme.cl"), any())).thenReturn(dto);

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/oposicion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.oposicionProcesamiento").value(true));
    }

    @Test
    void registrarOposicion_oponerseFaltante_retorna400YNoLlamaAlService() throws Exception {

        // ARRANGE — violación de @NotNull en OposicionInternoRequest

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/dueña@pyme.cl/oposicion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())));

        // ASSERT
        respuesta.andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(authService, org.mockito.Mockito.never()).registrarOposicion(any(), any());
    }

    @Test
    void registrarOposicion_cuentaNoExiste_retorna404() throws Exception {

        // ARRANGE
        when(authService.registrarOposicion(eq("nadie@pyme.cl"), any()))
                .thenThrow(new UsuarioNoEncontradoException("Usuario no encontrado"));

        // ACT
        var respuesta = mockMvc.perform(put("/auth/interno/usuarios/nadie@pyme.cl/oposicion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oponerse", true))));

        // ASSERT
        respuesta.andExpect(status().isNotFound());
    }
}
