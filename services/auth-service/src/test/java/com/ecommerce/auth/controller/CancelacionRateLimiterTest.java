package com.ecommerce.auth.controller;

import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración acotada — igual que RectificacionRateLimiterTest,
 * carga el contexto real de Spring Boot para ejercitar de verdad
 * cancelacionRateLimiter (un @WebMvcTest no carga el aspecto AOP de
 * Resilience4j), ver arco-cancelacion-oposicion.md §6.1/§7 A04, criterio de
 * aceptación 12 ("6 rapid attempts with wrong passwordActual... the 6th
 * returns 429").
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "jwt.secret=dGVzdC1qd3Qtc2VjcmV0LXBhcmEtY2FuY2VsYWNpb24tcmF0ZS1saW1pdGVyLWl0",
                "internal.service.secret=test-internal-secret-para-cancelacion-rate-limiter-it"
        }
)
@AutoConfigureMockMvc
class CancelacionRateLimiterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private UserRepository userRepository;

    @Value("${internal.service.secret}")
    private String secret;

    @Test
    void iniciarCancelacion_6IntentosRapidosConPasswordIncorrecta_elSextoRetorna429NoUnSexto401() throws Exception {

        // ARRANGE — la contraseña real de la cuenta nunca coincide con la
        // enviada, así que cada intento hasta el límite debe dar 401 (no 200)
        String hashReal = new BCryptPasswordEncoder(12).encode("password-real-de-la-cuenta");
        User user = User.builder().email("victima@pyme.cl").password(hashReal).role("ROLE_USER").build();
        when(userRepository.findByEmail("victima@pyme.cl")).thenReturn(Optional.of(user));

        String body = objectMapper.writeValueAsString(Map.of(
                "passwordActual", "intento-incorrecto", "confirmacion", "ELIMINAR_MI_CUENTA"));

        // ACT — 5 intentos rechazados por contraseña incorrecta (401)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/auth/interno/usuarios/victima@pyme.cl/cancelacion/iniciar")
                        .with(internalAuthHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // ASSERT — el 6to debe ser bloqueado por cancelacionRateLimiter,
        // no un 6to 401 (prueba que el oráculo de contraseña está mitigado)
        mockMvc.perform(put("/auth/interno/usuarios/victima@pyme.cl/cancelacion/iniciar")
                    .with(internalAuthHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isTooManyRequests());
    }

    private RequestPostProcessor internalAuthHeaders() {
        return request -> {
            long timestamp = System.currentTimeMillis();
            request.addHeader("X-Internal-Service", "api-gateway");
            request.addHeader("X-Internal-Timestamp", String.valueOf(timestamp));
            request.addHeader("X-Internal-Signature", sign(timestamp));
            return request;
        };
    }

    private String sign(long timestamp) {
        try {
            String stringToSign = "api-gateway:" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
