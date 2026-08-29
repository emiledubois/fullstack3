package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.CancelacionInternaDTO;
import com.ecommerce.auth.dto.CancelacionInternoRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResult;
import com.ecommerce.auth.dto.OposicionInternaDTO;
import com.ecommerce.auth.dto.OposicionInternoRequest;
import com.ecommerce.auth.dto.RectificacionInternaDTO;
import com.ecommerce.auth.dto.RectificarEmailInternoRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UsuarioInternoDTO;
import com.ecommerce.auth.exception.ConfirmacionInvalidaException;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String CONFIRMACION_ESPERADA = "ELIMINAR_MI_CUENTA";
    private static final String STATUS_ACTIVA = "ACTIVA";
    private static final String STATUS_CANCELACION_EN_PROGRESO = "CANCELACION_EN_PROGRESO";
    private static final String STATUS_CANCELADA = "CANCELADA";

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

        // Chequeado DESPUÉS de verificar la contraseña — la respuesta pública
        // solo revela este estado a alguien que ya demostró conocer la
        // contraseña correcta (misma disciplina de timing de disclosure que
        // rectificación, diseño §7 A07). El mensaje público se mantiene
        // genérico (anti-enumeración) — lo que distingue este caso de un
        // simple password incorrecto es la línea de log, no la respuesta.
        if (STATUS_CANCELACION_EN_PROGRESO.equals(user.getStatus())) {
            log.warn("[ARCO+] Login rechazado — cuenta en proceso de cancelación — email: {}", req.getEmail());
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
                .map(u -> new UsuarioInternoDTO(u.getEmail(), u.getRole(), u.getCreatedAt(),
                        Boolean.TRUE.equals(u.getOposicionProcesamiento()), u.getOposicionRegistradaEn()));
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

    /**
     * Llamado internamente por api-gateway (PUT /auth/interno/usuarios/{email}
     * /cancelacion/iniciar) — derecho de cancelación ARCO+ (arco-cancelacion-
     * oposicion.md §6.1/§6.2). {@code email} es siempre el derivado
     * server-side de la cookie verificada del llamador. Idempotente: una
     * cuenta ya en CANCELACION_EN_PROGRESO puede reintentar este mismo paso
     * — cancelacionSolicitadaEn se fija solo si aún era null, para preservar
     * el momento de la solicitud original a través de reintentos (diseño §7 A04).
     */
    public CancelacionInternaDTO iniciarCancelacion(String email, CancelacionInternoRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        if (!passwordEncoder.matches(req.getPasswordActual(), user.getPassword())) {
            log.info("[ARCO+] Cancelación rechazada — email={}, motivo=contraseña incorrecta", email);
            throw new InvalidCredentialsException("Contraseña actual incorrecta");
        }

        // Re-verificado aquí también (defensa en profundidad, §6.2) — nunca
        // confiar en que la validación del gateway sea el único chequeo.
        if (!CONFIRMACION_ESPERADA.equals(req.getConfirmacion())) {
            throw new ConfirmacionInvalidaException(
                    "Debe escribir exactamente " + CONFIRMACION_ESPERADA + " para confirmar");
        }

        String estadoActual = user.getStatus();
        if (estadoActual != null && !STATUS_ACTIVA.equals(estadoActual)
                && !STATUS_CANCELACION_EN_PROGRESO.equals(estadoActual)) {
            // No debería ocurrir en el flujo normal: una cuenta CANCELADA ya
            // tiene el email anonimizado, así que findByEmail de arriba no la
            // habría encontrado bajo el email original — defensivo de todas formas.
            throw new UsuarioNoEncontradoException("Usuario no encontrado");
        }

        if (user.getCancelacionSolicitadaEn() == null) {
            user.setCancelacionSolicitadaEn(LocalDateTime.now());
        }
        user.setStatus(STATUS_CANCELACION_EN_PROGRESO);
        userRepository.save(user);

        log.info("[ARCO+] Cancelación iniciada — email={}", email);
        return toCancelacionDTO(user);
    }

    /**
     * Llamado internamente por api-gateway cuando el chequeo de bloqueo
     * (pedidos/envíos activos) determina que la cuenta NO puede cancelarse
     * todavía (diseño §6.1 paso 2). Revierte la cuenta a ACTIVA sin tocar
     * ningún dato de pedidos/pagos.
     */
    public CancelacionInternaDTO revertirCancelacion(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        user.setStatus(STATUS_ACTIVA);
        user.setCancelacionSolicitadaEn(null);
        userRepository.save(user);

        log.info("[ARCO+] Cancelación revertida (pedidos/envíos activos) — email={}", email);
        return toCancelacionDTO(user);
    }

    /**
     * Llamado internamente por api-gateway una vez que ms-pedidos y ms-pagos
     * confirmaron la anonimización (diseño §6.1 paso 4). Busca la fila por
     * {@code id} (identificador numérico estable capturado en "iniciar"),
     * NUNCA por email — bajo doble-submit concurrente, una llamada a
     * finalizar ya exitosa puede haber cambiado el email de la fila antes de
     * que esta segunda llamada llegue (diseño §7 A04). Si la fila ya está
     * CANCELADA, se trata como éxito idempotente, no como error.
     */
    public CancelacionInternaDTO finalizarCancelacion(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        if (STATUS_CANCELADA.equals(user.getStatus())) {
            // Doble-submit: otra petición concurrente ya finalizó esta misma
            // cuenta — no-op idempotente, no un error (diseño §7 A04).
            return toCancelacionDTO(user);
        }

        user.setEmail("usuario-eliminado-" + user.getId() + "@smartlogix.invalid");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(STATUS_CANCELADA);
        user.setCancelacionCompletadaEn(LocalDateTime.now());
        userRepository.save(user);

        // Se loguea el id estable, NUNCA el email (ya anonimizado ni el
        // original) — el rastro de auditoría debe seguir siendo trazable a
        // una cuenta específica sin reintroducir el PII ya borrado en los
        // logs (diseño §7 A09).
        log.info("[ARCO+] Cancelación completada — id={}", user.getId());
        return toCancelacionDTO(user);
    }

    /**
     * Llamado internamente por api-gateway (PUT /auth/interno/usuarios/{email}
     * /oposicion) — derecho de oposición ARCO+ (diseño §6.5). No es un
     * ratchet de un solo sentido: oposicionRegistradaEn siempre se actualiza
     * a la fecha de la llamada más reciente, incluso al volver a "false".
     */
    public OposicionInternaDTO registrarOposicion(String email, OposicionInternoRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        user.setOposicionProcesamiento(req.getOponerse());
        user.setOposicionRegistradaEn(LocalDateTime.now());
        userRepository.save(user);

        log.info("[ARCO+] Oposición registrada — email={}, oponerse={}", email, req.getOponerse());
        return new OposicionInternaDTO(user.getOposicionProcesamiento(), user.getOposicionRegistradaEn());
    }

    private CancelacionInternaDTO toCancelacionDTO(User user) {
        return new CancelacionInternaDTO(user.getId(), user.getEmail(), user.getStatus(),
                user.getCancelacionSolicitadaEn(), user.getCancelacionCompletadaEn());
    }
}
