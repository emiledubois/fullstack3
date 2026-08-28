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
 * Prueba de integración acotada — a diferencia de UsuarioInternoControllerTest
 * (@WebMvcTest, que no carga el aspecto AOP de Resilience4j), esta prueba
 * carga el contexto real de Spring Boot para ejercitar de verdad
 * rectificacionRateLimiter, ver arco-remaining-rights.md §4.1/§7 A04,
 * criterio de aceptación 7 ("proves the password-oracle mitigation is
 * wired, not just documented"). Excluye datasource/JPA (no hay Postgres en
 * este entorno de test) y mockea UserRepository para poder levantar el
 * contexto sin una BD real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                // jwt.secret/internal.service.secret normalmente vienen de
                // JWT_SECRET/INTERNAL_SERVICE_SECRET (docker-compose/.env) —
                // se fijan aquí explícitamente porque este test no depende
                // del entorno de ejecución local para poder levantar el
                // contexto real de Spring (necesario para ejercitar el
                // aspecto de Resilience4j, ver comentario de clase).
                "jwt.secret=dGVzdC1qd3Qtc2VjcmV0LXBhcmEtcmVjdGlmaWNhY2lvbi1yYXRlLWxpbWl0ZXItaXQ=",
                "internal.service.secret=test-internal-secret-para-rectificacion-rate-limiter-it"
        }
)
@AutoConfigureMockMvc
class RectificacionRateLimiterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private UserRepository userRepository;

    @Value("${internal.service.secret}")
    private String secret;

    @Test
    void rectificarEmail_6IntentosRapidosConPasswordIncorrecta_elSextoRetorna429NoUnSexto401() throws Exception {

        // ARRANGE — la contraseña real de la cuenta nunca coincide con la
        // enviada, así que cada intento hasta el límite debe dar 401 (no 200)
        String hashReal = new BCryptPasswordEncoder(12).encode("password-real-de-la-cuenta");
        User user = User.builder().email("victima@pyme.cl").password(hashReal).role("ROLE_USER").build();
        when(userRepository.findByEmail("victima@pyme.cl")).thenReturn(Optional.of(user));

        String body = objectMapper.writeValueAsString(Map.of(
                "emailNuevo", "nueva@pyme.cl", "passwordActual", "intento-incorrecto"));

        // ACT — 5 intentos rechazados por contraseña incorrecta (401)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/auth/interno/usuarios/victima@pyme.cl")
                        .with(internalAuthHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // ASSERT — el 6to debe ser bloqueado por rectificacionRateLimiter,
        // no un 6to 401 (prueba que el oráculo de contraseña está mitigado)
        mockMvc.perform(put("/auth/interno/usuarios/victima@pyme.cl")
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
