package com.ecommerce.api.gateway.controller;

import com.ecommerce.api.gateway.dto.*;
import com.ecommerce.api.gateway.security.InternalTokenSigner;
import com.ecommerce.api.gateway.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebHub / Backend for Frontend (BFF) — Derecho de Acceso ARCO+
 *
 * GET /api/usuarios/me/datos
 *
 * Agrega los datos personales del USUARIO QUE LLAMA (nunca de otro usuario:
 * la identidad se deriva exclusivamente de la cookie sl_jwt verificada, igual
 * que SessionController — nunca de un parámetro de la petición) desde:
 *   - auth-service  (cuenta: email, role, fecha de creación)
 *   - ms-pedidos    (historial de pedidos propios)
 *   - ms-envios     (envíos de esos pedidos — Envio no tiene columna de
 *                     identidad propia, así que depende del resultado de
 *                     ms-pedidos: NO es un fan-out totalmente paralelo como
 *                     DashboardController, ver arco-acceso-personal-data.md §5)
 *
 * NOTA DE ALCANCE: ms-pagos (referencia Flow/estado/monto) NO se incluye en
 * esta iteración — ver diseño §2/§7/§9, fast-follow explícitamente flagueado,
 * no un olvido.
 */
@RestController
@RequestMapping("/usuarios")
@Slf4j
public class UsuarioDatosController {

    private static final String COOKIE_NAME = "sl_jwt";

    private final JwtUtil jwtUtil;
    private final WebClient authClient;
    private final WebClient pedidosClient;
    private final WebClient enviosClient;

    public UsuarioDatosController(
            JwtUtil jwtUtil,
            @Value("${auth.service.url:http://auth-service:8081}")    String authUrl,
            @Value("${pedidos.service.url:http://ms-pedidos:8083}")   String pedUrl,
            @Value("${envios.service.url:http://ms-envios:8084}")     String envUrl,
            WebClient.Builder builder,
            InternalTokenSigner internalTokenSigner) {
        this.jwtUtil = jwtUtil;
        this.authClient    = builder.clone().baseUrl(authUrl).filter(internalTokenSigner.exchangeFilter()).build();
        this.pedidosClient = builder.clone().baseUrl(pedUrl).filter(internalTokenSigner.exchangeFilter()).build();
        this.enviosClient  = builder.clone().baseUrl(envUrl).filter(internalTokenSigner.exchangeFilter()).build();
    }

    @GetMapping("/me/datos")
    public ResponseEntity<UsuarioDatosDTO> getMisDatos(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String email = jwtUtil.extractEmail(token);
        // Auditoría de acceso ARCO+ (Ley 21.719, COMPLIANCE_CL.md §4.4) —
        // registrar SOLO email+timestamp, nunca el contenido de la respuesta.
        log.info("[ARCO+] Solicitud de acceso a datos personales — email={}", email);

        UsuarioDatosDTO body = agregarDatos(email);
        return ResponseEntity.ok(body);
    }

    /**
     * GET /usuarios/me/datos/exportar — Derecho de Portabilidad ARCO+
     * (arco-remaining-rights.md §4.3).
     *
     * Reutiliza EXACTAMENTE la misma agregación de agregarDatos(email) que
     * getMisDatos — sin nueva lógica de agregación, solo el envoltorio de
     * exportación versionado (formatoVersion/tipoSolicitud) y el
     * Content-Disposition que hace descargable la respuesta como archivo.
     * No "mejora" un estadoAgregacion degradado: un export parcial se
     * etiqueta como tal, igual que el endpoint de acceso.
     */
    @GetMapping("/me/datos/exportar")
    public ResponseEntity<ExportacionDatosDTO> exportarMisDatos(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String email = jwtUtil.extractEmail(token);
        // Auditoría distinta de la de acceso (§7 A09) — dos derechos
        // legalmente distintos deben quedar auditables por separado.
        log.info("[ARCO+] Solicitud de portabilidad — email={}", email);

        ExportacionDatosDTO body = ExportacionDatosDTO.builder()
            .formatoVersion("1.0")
            .tipoSolicitud("PORTABILIDAD_ARCO")
            .titular(email)
            .generadoEn(LocalDateTime.now())
            .datos(agregarDatos(email))
            .build();

        String nombreArchivo = "smartlogix-mis-datos-"
            + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".json";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
            .body(body);
    }

    private UsuarioDatosDTO agregarDatos(String email) {
        Mono<Resultado<CuentaDTO>> cuentaMono = authClient.get()
            .uri("/auth/interno/usuarios/{email}", email)
            .retrieve()
            .bodyToMono(CuentaDTO.class)
            .map(c -> new Resultado<>(c, true))
            .onErrorResume(WebClientResponseException.NotFound.class,
                e -> Mono.just(new Resultado<>(null, true)))
            .onErrorReturn(new Resultado<>(null, false));

        Mono<Resultado<List<PedidoDatosDTO>>> pedidosMono = pedidosClient.get()
            .uri("/pedidos/interno/por-email/{email}", email)
            .retrieve()
            .bodyToFlux(PedidoDatosDTO.class)
            .collectList()
            .map(l -> new Resultado<>(l, true))
            .onErrorReturn(new Resultado<>(Collections.emptyList(), false));

        return Mono.zip(cuentaMono, pedidosMono)
            .flatMap(tuple -> {
                Resultado<CuentaDTO> cuenta = tuple.getT1();
                Resultado<List<PedidoDatosDTO>> pedidos = tuple.getT2();
                List<Long> pedidoIds = pedidos.valor.stream()
                    .map(PedidoDatosDTO::getId).collect(Collectors.toList());

                if (pedidoIds.isEmpty()) {
                    // Sin pedidos propios (o ms-pedidos falló y devolvió []):
                    // no tiene sentido llamar a ms-envios — nunca habría match.
                    return Mono.just(construir(email, cuenta, pedidos, new Resultado<>(Collections.emptyList(), true)));
                }

                String idsParam = pedidoIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                Mono<Resultado<List<EnvioDatosDTO>>> enviosMono = enviosClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/envios/interno/por-pedidos")
                        .queryParam("pedidoIds", idsParam).build())
                    .retrieve()
                    .bodyToFlux(EnvioDatosDTO.class)
                    .collectList()
                    .map(l -> new Resultado<>(l, true))
                    .onErrorReturn(new Resultado<>(Collections.emptyList(), false));

                return enviosMono.map(envios -> construir(email, cuenta, pedidos, envios));
            })
            .block();
    }

    private UsuarioDatosDTO construir(
            String email,
            Resultado<CuentaDTO> cuenta,
            Resultado<List<PedidoDatosDTO>> pedidos,
            Resultado<List<EnvioDatosDTO>> envios) {

        return UsuarioDatosDTO.builder()
            .generadoEn(LocalDateTime.now())
            .estadoAgregacion(determinarEstado(cuenta.ok, pedidos.ok, envios.ok, !pedidos.valor.isEmpty()))
            .cuenta(cuenta.valor)
            .pedidos(pedidos.valor)
            .envios(envios.valor)
            .build();
    }

    // ms-envios solo se "intenta de verdad" cuando hay pedidos que consultar
    // (enviosIntentado). Si no, su ok=true trivial no debe contar ni a favor
    // ni en contra del estado — solo se promedian las fuentes realmente
    // consultadas (ver arco-acceso-personal-data.md §3.1: OK/PARCIAL/ERROR
    // debe distinguir "sin datos" de "fuente caída", a diferencia de
    // DashboardController.determinarEstado que no necesita esa distinción).
    private String determinarEstado(boolean cuentaOk, boolean pedidosOk, boolean enviosOk, boolean enviosIntentado) {
        int total = 2 + (enviosIntentado ? 1 : 0);
        int fallos = (cuentaOk ? 0 : 1) + (pedidosOk ? 0 : 1) + (enviosIntentado && !enviosOk ? 1 : 0);
        if (fallos == 0) return "OK";
        if (fallos == total) return "ERROR";
        return "PARCIAL";
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies)
            .filter(c -> COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    @AllArgsConstructor
    private static class Resultado<T> {
        private final T valor;
        private final boolean ok;
    }
}
