package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.CancelacionInternaDTO;
import com.ecommerce.api.gateway.dto.CancelacionRequest;
import com.ecommerce.api.gateway.dto.EnvioDatosDTO;
import com.ecommerce.api.gateway.dto.PedidoDatosDTO;
import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebHub / Backend for Frontend (BFF) — Derecho de Cancelación ARCO+
 *
 * POST /api/usuarios/me/cancelacion
 *
 * Facade (no Saga): orquesta una transición de estado persistida, idempotente
 * y de un solo sentido (ACTIVA → CANCELACION_EN_PROGRESO → CANCELADA) sobre
 * la cuenta del USUARIO QUE LLAMA — identidad derivada exclusivamente de la
 * cookie sl_jwt verificada, igual que RectificacionController/
 * UsuarioDatosController. No hay compensación posible para "un dato fue
 * anonimizado" — la semántica correcta es reintentar la misma operación
 * idempotente hacia adelante hasta que todos los servicios confirmen (ver
 * arco-cancelacion-oposicion.md §5).
 */
@RestController
@RequestMapping("/usuarios")
@Slf4j
public class CancelacionController {

    private static final String COOKIE_NAME = "sl_jwt";
    private static final String CONFIRMACION_ESPERADA = "ELIMINAR_MI_CUENTA";

    private static final Set<String> PEDIDO_TERMINAL =
            Set.of("ENTREGADO", "CANCELADO", "PAGO_RECHAZADO", "PAGO_ANULADO");
    private static final Set<String> ENVIO_TERMINAL = Set.of("ENTREGADO", "CANCELADO");

    private final JwtUtil jwtUtil;
    private final WebClient authClient;
    private final WebClient pedidosClient;
    private final WebClient enviosClient;
    private final WebClient pagosClient;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    public CancelacionController(
            JwtUtil jwtUtil,
            @Value("${auth.service.url:http://auth-service:8081}")    String authUrl,
            @Value("${pedidos.service.url:http://ms-pedidos:8083}")   String pedUrl,
            @Value("${envios.service.url:http://ms-envios:8084}")     String envUrl,
            @Value("${pagos.service.url:http://ms-pagos:8086}")       String pagUrl,
            WebClient.Builder builder,
            InternalTokenSigner internalTokenSigner) {
        this.jwtUtil = jwtUtil;
        this.authClient    = builder.clone().baseUrl(authUrl).filter(internalTokenSigner.exchangeFilter()).build();
        this.pedidosClient = builder.clone().baseUrl(pedUrl).filter(internalTokenSigner.exchangeFilter()).build();
        this.enviosClient  = builder.clone().baseUrl(envUrl).filter(internalTokenSigner.exchangeFilter()).build();
        this.pagosClient   = builder.clone().baseUrl(pagUrl).filter(internalTokenSigner.exchangeFilter()).build();
    }

    @PostMapping("/me/cancelacion")
    public ResponseEntity<?> cancelarCuenta(
            @Valid @RequestBody CancelacionRequest req,
            HttpServletRequest request) {

        String token = extractToken(request);
        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Validado aquí (no vía Bean Validation) para poder devolver un
        // mensaje específico — y para NO llamar a auth-service en absoluto
        // si la confirmación es incorrecta (criterio de aceptación 2: sin
        // cambio de estado, ni siquiera un intento de "iniciar").
        if (!CONFIRMACION_ESPERADA.equals(req.getConfirmacion())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Debe escribir exactamente " + CONFIRMACION_ESPERADA + " para confirmar"));
        }

        String email = jwtUtil.extractEmail(token);

        // Paso 1: step-up-auth + transición a CANCELACION_EN_PROGRESO
        CancelacionInternaDTO iniciado;
        try {
            iniciado = authClient.put()
                    .uri("/auth/interno/usuarios/{email}/cancelacion/iniciar", email)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(CancelacionInternaDTO.class)
                    .block();
        } catch (WebClientResponseException e) {
            // 401 (password incorrecta) / 404 (cuenta ya no resuelve) / 429
            // (rate limit) — auth-service ya determinó y logueó el motivo.
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsByteArray());
        }

        // Paso 2: chequeo de bloqueo — re-ejecutado en CADA llamada, incluso
        // reintentos (§7 A04: una carrera con un pedido creado durante la
        // ventana CANCELACION_EN_PROGRESO debe volver a descubrirse aquí).
        List<Long> pedidosBloqueantes;
        try {
            pedidosBloqueantes = obtenerPedidosBloqueantes(email);
        } catch (Exception e) {
            // No se pudo determinar el bloqueo de forma confiable (ms-pedidos/
            // ms-envios caídos) — fail-safe: NO se revierte a ACTIVA (no
            // sabemos si está bloqueada) ni se destruye ningún dato. La
            // cuenta queda congelada para reintentar, mismo tratamiento que
            // agotar los reintentos del paso 3.
            log.warn("[ARCO+] Cancelación — no se pudo verificar bloqueo de pedidos/envíos, email={}", email);
            return respuestaParcial();
        }

        if (!pedidosBloqueantes.isEmpty()) {
            revertirCancelacion(email);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "No puede cancelar su cuenta mientras tenga pedidos o envíos en curso.",
                            "pedidosBloqueantes", pedidosBloqueantes));
        }

        // Paso 3: anonimización en paralelo, cada una con reintento — ambas
        // son UPDATE idempotentes (una fila ya anonimizada simplemente no
        // matchea el WHERE en un reintento).
        if (!anonimizarPedidosYPagos(email)) {
            return respuestaParcial();
        }

        // Paso 4: finalizar — SIEMPRE por id (capturado en el paso 1), nunca
        // re-resolviendo por email (carrera de doble-submit, §7 A04).
        authClient.put()
                .uri(uriBuilder -> uriBuilder.path("/auth/interno/usuarios/{email}/cancelacion/finalizar")
                        .queryParam("id", iniciado.getId()).build(email))
                .retrieve()
                .bodyToMono(CancelacionInternaDTO.class)
                .block();

        ResponseCookie cookieExpirada = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieExpirada.toString())
                .body(Map.of("estado", "COMPLETADA", "mensaje", "Su cuenta y sus datos personales han sido eliminados."));
    }

    private ResponseEntity<Map<String, String>> respuestaParcial() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("estado", "PARCIAL",
                        "mensaje", "No se pudo completar la eliminación en todos los sistemas. Vuelva a intentarlo en unos minutos."));
    }

    // Mismo esquema de dos saltos que UsuarioDatosController.agregarDatos:
    // pedidos-por-email, luego (si no está vacío) envíos-por-pedidos.
    // Allowlist de estados terminales, default-deny para cualquier valor no
    // reconocido (arco-cancelacion-oposicion.md §6.1 paso 2).
    private List<Long> obtenerPedidosBloqueantes(String email) {
        List<PedidoDatosDTO> pedidos = pedidosClient.get()
                .uri("/pedidos/interno/por-email/{email}", email)
                .retrieve()
                .bodyToFlux(PedidoDatosDTO.class)
                .collectList()
                .block();

        if (pedidos == null || pedidos.isEmpty()) {
            return List.of();
        }

        Set<Long> bloqueantes = new LinkedHashSet<>(pedidos.stream()
                .filter(p -> !PEDIDO_TERMINAL.contains(p.getStatus()))
                .map(PedidoDatosDTO::getId)
                .collect(Collectors.toList()));

        String idsParam = pedidos.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
        List<EnvioDatosDTO> envios = enviosClient.get()
                .uri(uriBuilder -> uriBuilder.path("/envios/interno/por-pedidos")
                        .queryParam("pedidoIds", idsParam).build())
                .retrieve()
                .bodyToFlux(EnvioDatosDTO.class)
                .collectList()
                .block();

        if (envios != null) {
            envios.stream()
                    .filter(e -> !ENVIO_TERMINAL.contains(e.getStatus()))
                    .map(EnvioDatosDTO::getPedidoId)
                    .forEach(bloqueantes::add);
        }

        return List.copyOf(bloqueantes);
    }

    private void revertirCancelacion(String email) {
        try {
            authClient.put()
                    .uri("/auth/interno/usuarios/{email}/cancelacion/revertir", email)
                    .retrieve()
                    .bodyToMono(CancelacionInternaDTO.class)
                    .block();
        } catch (Exception e) {
            log.warn("[ARCO+] Cancelación — no se pudo revertir a ACTIVA tras bloqueo, email={}", email);
        }
    }

    // Ambas llamadas en paralelo (Mono.zip se suscribe a las dos antes de
    // esperar), cada una reintentada 3 veces con backoff exponencial desde
    // 300ms (arco-cancelacion-oposicion.md §6.1 paso 3) — reactor-core ya es
    // una dependencia transitiva de spring-boot-starter-webflux, no se agrega
    // ninguna librería nueva.
    private boolean anonimizarPedidosYPagos(String email) {
        Mono<Boolean> pedidosMono = pedidosClient.put()
                .uri("/pedidos/interno/por-email/{email}/anonimizar", email)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(300)))
                .map(r -> true)
                .onErrorReturn(false);

        Mono<Boolean> pagosMono = pagosClient.put()
                .uri("/pagos/interno/por-email/{email}/anonimizar", email)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(300)))
                .map(r -> true)
                .onErrorReturn(false);

        var resultado = Mono.zip(pedidosMono, pagosMono).block();
        return resultado != null && resultado.getT1() && resultado.getT2();
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
