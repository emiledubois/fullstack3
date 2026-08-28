package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.RectificarEmailInternoRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.EmailYaRegistradoException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.UsuarioNoEncontradoException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Llamado internamente por api-gateway (PUT /auth/interno/usuarios/{email})
     * para el derecho de rectificación ARCO+. {@code emailActual} es siempre
     * el email derivado server-side de la cookie verificada del llamador —
     * nunca un valor tomado del body (arco-remaining-rights.md §4.2/§7 A01).
     * Requiere re-verificar passwordActual (mitigación de password-oracle,
     * §7 A04) antes de tocar el email. Devuelve un JWT nuevo con
     * sub=emailNuevo para que el gateway rote la cookie de sesión.
     */
    public RectificacionInternaDTO rectificarEmail(String emailActual, RectificarEmailInternoRequest req) {
        User user = userRepository.findByEmail(emailActual)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        if (!passwordEncoder.matches(req.getPasswordActual(), user.getPassword())) {
            log.info("[ARCO+] Rectificación de email rechazada — email={}, motivo=contraseña incorrecta", emailActual);
            throw new InvalidCredentialsException("Contraseña actual incorrecta");
        }

        String emailNuevo = req.getEmailNuevo();
        // emailNuevo == email actual (case-sensitive): no-op tratado como
        // éxito, no como conflicto (ver diseño §4.1/§8) — no hay nada que
        // chequear/guardar, pero igual se rota la cookie más abajo.
        if (!emailNuevo.equals(user.getEmail())) {
            if (userRepository.existsByEmail(emailNuevo)) {
                log.info("[ARCO+] Rectificación de email rechazada — email={}, emailNuevo={}, motivo=email ya registrado",
                        emailActual, emailNuevo);
                throw new EmailYaRegistradoException("Este email ya está registrado");
            }
            user.setEmail(emailNuevo);
            try {
                // saveAndFlush (no solo save): fuerza el UPDATE — y por lo
                // tanto la violación de la restricción unique — a ocurrir
                // dentro de esta llamada, cerrando el TOCTOU entre el
                // existsByEmail de arriba y el guardado bajo concurrencia
                // (mismo tipo de excepción sin manejar que QA ya atrapó una
                // vez en internal-service-auth.md — no debe reaparecer aquí
                // como un 500 crudo).
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                log.info("[ARCO+] Rectificación de email rechazada — email={}, emailNuevo={}, motivo=email ya registrado",
                        emailActual, emailNuevo);
                throw new EmailYaRegistradoException("Este email ya está registrado");
            }
        }

        log.info("[ARCO+] Rectificación de email — anterior={}, nuevo={}", emailActual, user.getEmail());
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new RectificacionInternaDTO(user.getEmail(), user.getRole(), user.getCreatedAt(), token);
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
