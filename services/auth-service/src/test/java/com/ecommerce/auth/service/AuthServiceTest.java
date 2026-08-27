package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
}
