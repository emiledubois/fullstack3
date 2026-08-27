package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email ya registrado");
        }

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
        return "Usuario registrado exitosamente";
    }

    public LoginResult login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> {
                    log.info("Login fallido — email: {}", req.getEmail());
                    return new InvalidCredentialsException("Credenciales inválidas");
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.info("Login fallido — email: {}", req.getEmail());
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        log.info("Login exitoso — email: {}", user.getEmail());
        return new LoginResult(token, user.getEmail(), user.getRole(),
                jwtUtil.extractExpiration(token), jwtUtil.getExpirationSeconds());
    }

    /**
     * Llamado internamente por api-gateway (GET /auth/interno/usuarios/{email})
     * para el endpoint de derecho de acceso ARCO+. Nunca devuelve la entidad
     * User (tiene password) — solo el DTO dedicado, sin campo password.
     */
    public Optional<UsuarioInternoDTO> buscarUsuarioInterno(String email) {
        return userRepository.findByEmail(email)
                .map(u -> new UsuarioInternoDTO(u.getEmail(), u.getRole(), u.getCreatedAt()));
    }

    // Stateless JWT: this only tells the caller "if you had a valid cookie,
    // here's who you were" for the audit log — it cannot revoke the token
    // itself (see design doc §4).
    public void logout(String token) {
        if (token != null && jwtUtil.isValid(token)) {
            log.info("Logout — email: {}", jwtUtil.extractEmail(token));
        }
    }
}
