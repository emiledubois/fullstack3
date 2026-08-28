package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.RectificarEmailInternoRequest;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.EmailYaRegistradoException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.UsuarioNoEncontradoException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository   userRepository;
    @Mock private PasswordEncoder  passwordEncoder;
    @Mock private JwtUtil          jwtUtil;
    @InjectMocks private AuthService authService;

    @Test
    void login_credencialesValidas_retornaTokenEmailRoleYExpiracion() {

        // ARRANGE
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@pyme.cl");
        req.setPassword("Password123!");

        User user = User.builder()
                .email("admin@pyme.cl")
                .password("hash")
                .role("ROLE_USER")
                .build();

        when(userRepository.findByEmail("admin@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(jwtUtil.generateToken("admin@pyme.cl", "ROLE_USER")).thenReturn("jwt-token");
        Instant expected = Instant.now().plusSeconds(86400);
        when(jwtUtil.extractExpiration("jwt-token")).thenReturn(expected);
        when(jwtUtil.getExpirationSeconds()).thenReturn(86400L);

        // ACT
        LoginResult result = authService.login(req);

        // ASSERT
        // El token nunca debe faltar internamente (lo usa el controller para la cookie)
        assertEquals("jwt-token", result.getToken());
        assertEquals("admin@pyme.cl", result.getEmail());
        assertEquals("ROLE_USER", result.getRole());
        assertEquals(expected, result.getExpiresAt());
        assertEquals(86400L, result.getMaxAgeSeconds());
    }

    @Test
    void login_emailNoExiste_lanzaInvalidCredentialsException() {

        // ARRANGE
        LoginRequest req = new LoginRequest();
        req.setEmail("noexiste@pyme.cl");
        req.setPassword("cualquiera");

        when(userRepository.findByEmail("noexiste@pyme.cl")).thenReturn(Optional.empty());

        // ACT
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(req));

        // ASSERT
        // Mismo mensaje genérico que para password incorrecta — no debe filtrar
        // si el email existe o no (evita enumeración de usuarios)
        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void login_passwordIncorrecta_lanzaInvalidCredentialsException() {

        // ARRANGE
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@pyme.cl");
        req.setPassword("incorrecta");

        User user = User.builder()
                .email("admin@pyme.cl")
                .password("hash")
                .role("ROLE_USER")
                .build();

        when(userRepository.findByEmail("admin@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        // ACT
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(req));

        // ASSERT
        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void logout_conTokenValido_noLanzaExcepcion() {

        // ARRANGE
        when(jwtUtil.isValid("jwt-token")).thenReturn(true);
        when(jwtUtil.extractEmail("jwt-token")).thenReturn("admin@pyme.cl");

        // ACT & ASSERT
        // Logout es idempotente y nunca falla — solo registra el evento si el
        // token es válido, sin revocar nada (JWT sin estado, ver diseño §4)
        assertDoesNotThrow(() -> authService.logout("jwt-token"));
    }

    @Test
    void logout_sinToken_noLanzaExcepcionYNoConsultaJwtUtil() {

        // ARRANGE — sin cookie presente (token == null)

        // ACT & ASSERT
        assertDoesNotThrow(() -> authService.logout(null));
        verify(jwtUtil, never()).extractEmail(any());
    }

    @Test
    void buscarUsuarioInterno_emailExiste_retornaDTOSinPassword() {

        // ARRANGE
        User user = User.builder()
                .email("dueña@pyme.cl").password("hash-secreto")
                .role("ROLE_USER").createdAt(LocalDateTime.of(2026, 1, 15, 9, 0))
                .build();
        when(userRepository.findByEmail("dueña@pyme.cl")).thenReturn(Optional.of(user));

        // ACT
        Optional<UsuarioInternoDTO> resultado = authService.buscarUsuarioInterno("dueña@pyme.cl");

        // ASSERT
        assertTrue(resultado.isPresent());
        assertEquals("dueña@pyme.cl", resultado.get().getEmail());
        assertEquals("ROLE_USER", resultado.get().getRole());
        assertEquals(LocalDateTime.of(2026, 1, 15, 9, 0), resultado.get().getCuentaCreadaEn());
    }

    @Test
    void buscarUsuarioInterno_emailNoExiste_retornaOptionalVacio() {

        // ARRANGE
        when(userRepository.findByEmail("nadie@pyme.cl")).thenReturn(Optional.empty());

        // ACT
        Optional<UsuarioInternoDTO> resultado = authService.buscarUsuarioInterno("nadie@pyme.cl");

        // ASSERT
        assertTrue(resultado.isEmpty());
    }

    @Test
    void rectificarEmail_passwordCorrectaYEmailNuevoDisponible_actualizaEmailYRetornaTokenNuevo() {

        // ARRANGE
        User user = User.builder()
                .email("vieja@pyme.cl").password("hash").role("ROLE_USER")
                .createdAt(LocalDateTime.of(2026, 1, 15, 9, 0)).build();
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("correcta@pyme.cl");
        req.setPasswordActual("Password123!");

        when(userRepository.findByEmail("vieja@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("correcta@pyme.cl")).thenReturn(false);
        when(jwtUtil.generateToken("correcta@pyme.cl", "ROLE_USER")).thenReturn("jwt-nuevo");

        // ACT
        RectificacionInternaDTO resultado = authService.rectificarEmail("vieja@pyme.cl", req);

        // ASSERT
        assertEquals("correcta@pyme.cl", resultado.getEmail());
        assertEquals("ROLE_USER", resultado.getRole());
        assertEquals("jwt-nuevo", resultado.getToken());
        assertEquals(LocalDateTime.of(2026, 1, 15, 9, 0), resultado.getCuentaCreadaEn());
        verify(userRepository).saveAndFlush(argThat(u -> "correcta@pyme.cl".equals(u.getEmail())));
    }

    @Test
    void rectificarEmail_passwordActualIncorrecta_lanzaInvalidCredentialsExceptionYNoModificaNada() {

        // ARRANGE — abuso crítico: el oráculo de contraseña que justifica
        // rectificacionRateLimiter (diseño §7 A04)
        User user = User.builder().email("vieja@pyme.cl").password("hash").role("ROLE_USER").build();
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("correcta@pyme.cl");
        req.setPasswordActual("incorrecta");

        when(userRepository.findByEmail("vieja@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        // ACT
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.rectificarEmail("vieja@pyme.cl", req));

        // ASSERT
        assertEquals("Contraseña actual incorrecta", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void rectificarEmail_emailNuevoYaRegistradoPorOtraCuenta_lanzaEmailYaRegistradoExceptionYNoGuarda() {

        // ARRANGE
        User user = User.builder().email("vieja@pyme.cl").password("hash").role("ROLE_USER").build();
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("otra-cuenta@pyme.cl");
        req.setPasswordActual("Password123!");

        when(userRepository.findByEmail("vieja@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("otra-cuenta@pyme.cl")).thenReturn(true);

        // ACT
        EmailYaRegistradoException ex = assertThrows(EmailYaRegistradoException.class,
                () -> authService.rectificarEmail("vieja@pyme.cl", req));

        // ASSERT
        assertEquals("Este email ya está registrado", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void rectificarEmail_cuentaDeLaCookieYaNoExiste_lanzaUsuarioNoEncontradoException() {

        // ARRANGE — cookie válida cuyo email ya no resuelve a una cuenta
        // (edge case documentado en arco-acceso-personal-data.md, reutilizado aquí)
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("nueva@pyme.cl");
        req.setPasswordActual("cualquiera");

        when(userRepository.findByEmail("borrada@pyme.cl")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(UsuarioNoEncontradoException.class,
                () -> authService.rectificarEmail("borrada@pyme.cl", req));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void rectificarEmail_emailNuevoIgualAlActual_esNoOpPeroRotaElToken() {

        // ARRANGE — criterio de idempotencia (diseño §4.1/§8): mismo email,
        // no debe consultar existsByEmail ni guardar, pero sí emitir un
        // token nuevo para que la cookie se rote igual
        User user = User.builder()
                .email("misma@pyme.cl").password("hash").role("ROLE_USER").build();
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("misma@pyme.cl");
        req.setPasswordActual("Password123!");

        when(userRepository.findByEmail("misma@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(jwtUtil.generateToken("misma@pyme.cl", "ROLE_USER")).thenReturn("jwt-rotado");

        // ACT
        RectificacionInternaDTO resultado = authService.rectificarEmail("misma@pyme.cl", req);

        // ASSERT
        assertEquals("misma@pyme.cl", resultado.getEmail());
        assertEquals("jwt-rotado", resultado.getToken());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rectificarEmail_conflictoDeConcurrenciaEnElGuardado_traduceExcepcionDeUnicidadA409YNoLanza500() {

        // ARRANGE — abuso de carrera (diseño §7 A04): existsByEmail dijo que
        // estaba libre, pero otra petición concurrente lo tomó justo antes
        // del guardado — el unique constraint de la BD es la red de
        // seguridad real, y su excepción no debe escapar como un 500 crudo
        // (mismo tipo de bug que internal-service-auth.md QA ya atrapó una vez)
        User user = User.builder().email("vieja@pyme.cl").password("hash").role("ROLE_USER").build();
        RectificarEmailInternoRequest req = new RectificarEmailInternoRequest();
        req.setEmailNuevo("disputada@pyme.cl");
        req.setPasswordActual("Password123!");

        when(userRepository.findByEmail("vieja@pyme.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("disputada@pyme.cl")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violado"));

        // ACT
        EmailYaRegistradoException ex = assertThrows(EmailYaRegistradoException.class,
                () -> authService.rectificarEmail("vieja@pyme.cl", req));

        // ASSERT
        assertEquals("Este email ya está registrado", ex.getMessage());
        verify(jwtUtil, never()).generateToken(any(), any());
    }
}
