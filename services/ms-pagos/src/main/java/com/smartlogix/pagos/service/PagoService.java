package com.smartlogix.pagos.service;

import com.smartlogix.pagos.dto.*;
import com.smartlogix.pagos.model.Pago;
import com.smartlogix.pagos.repository.PagoRepository;
import com.smartlogix.pagos.security.InternalAuthInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoService {

    private final PagoRepository pagoRepository;
    private final FlowService    flowService;
    private final InternalAuthInterceptor internalAuthInterceptor;

    @Value("${flow.url.confirmation}")       private String  urlConfirmation;
    @Value("${flow.url.return}")             private String  urlReturn;
    @Value("${pedidos.service.url}")         private String  pedidosUrl;
    // false en sandbox (Flow no envía firma), true en producción
    @Value("${flow.verify.signature:false}")  private boolean verifySignature;

    private final RestTemplate restTemplate = new RestTemplate();

    // Se agrega en @PostConstruct (no en el inicializador del campo) para
    // que las dos llamadas salientes a ms-pedidos (confirmar-pago,
    // pago-fallido) siempre vayan firmadas como ms-pagos.
    @PostConstruct
    void configurarRestTemplate() {
        restTemplate.getInterceptors().add(internalAuthInterceptor);
    }

    // Paso 1: iniciar pago con Flow 
    @Transactional
    public CrearPagoResponse iniciarPago(CrearPagoRequest req) {
        String commerceOrder = "SL-" + req.getPedidoId() + "-" + System.currentTimeMillis();

        FlowService.FlowCreateResponse flowResp = flowService.crearOrden(
            commerceOrder,
            req.getDescripcion() != null
                ? req.getDescripcion()
                : "Pedido SmartLogix #" + req.getPedidoId(),
            req.getMonto().intValue(),
            req.getEmail(),
            urlConfirmation,
            urlReturn
        );

        String urlPago = flowResp.getUrl() + "?token=" + flowResp.getToken();

        Pago pago = Pago.builder()
            .pedidoId(req.getPedidoId())
            .commerceOrder(commerceOrder)
            .flowToken(flowResp.getToken())
            .flowOrder(flowResp.getFlowOrder())
            .estado(Pago.EstadoPago.INICIADO)
            .monto(req.getMonto())
            .email(req.getEmail())
            .urlPago(urlPago)
            .build();
        pagoRepository.save(pago);

        log.info("[Pagos] Pago iniciado: pedidoId={}, token={}, urlPago={}",
            req.getPedidoId(), flowResp.getToken(), urlPago);

        return CrearPagoResponse.builder()
            .pagoId(pago.getId())
            .urlPago(urlPago)
            .flowToken(flowResp.getToken())
            .flowOrder(flowResp.getFlowOrder())
            .estado("INICIADO")
            .build();
    }

    // Paso 2: procesar webhook de confirmación de Flow 
    @Transactional
    public void procesarWebhook(String token, String firma) {
        log.info("[Pagos] Webhook recibido: token={}***",
            token.length() > 8 ? token.substring(0, 8) : "???");

        // Verificación de firma: obligatoria en producción,
        // opcional en sandbox (Flow Sandbox no siempre envía firma)
        if (verifySignature) {
            if (firma == null || firma.isBlank()) {
                log.error("[Pagos] Webhook rechazado: firma ausente");
                throw new RuntimeException("Firma requerida en webhook de Flow");
            }
            if (!flowService.verificarFirmaWebhook(token, firma)) {
                log.error("[Pagos] Webhook rechazado: firma inválida");
                throw new RuntimeException("Firma de webhook de Flow inválida");
            }
        } else {
            log.warn("[Pagos] Verificación de firma DESACTIVADA (modo sandbox)");
        }

        // Consultar estado real del pago en Flow
        FlowService.FlowStatusResponse estado = flowService.obtenerEstado(token);

        // Buscar el pago por token
        Pago pago = pagoRepository.findByFlowToken(token)
            .orElseThrow(() -> new RuntimeException("[Pagos] Token no encontrado: " + token));

        // Idempotencia: ignorar si ya fue procesado
        if (pago.getEstado() == Pago.EstadoPago.PAGADO
                || pago.getEstado() == Pago.EstadoPago.RECHAZADO
                || pago.getEstado() == Pago.EstadoPago.ANULADO) {
            log.info("[Pagos] Webhook duplicado ignorado. Token: {}***, Estado actual: {}",
                token.length() > 8 ? token.substring(0, 8) : "???", pago.getEstado());
            return;
        }

        // Actualizar estado según código de Flow:
        // 1=pendiente, 2=pagado, 3=rechazado, 4=anulado
        switch (estado.getStatus()) {
            case 2 -> {
                pago.setEstado(Pago.EstadoPago.PAGADO);
                pago.setConfirmadoEn(LocalDateTime.now());
                pagoRepository.save(pago);
                log.info("[Pagos] Pago EXITOSO: pedidoId={}, monto={}",
                    pago.getPedidoId(), estado.getAmount());
                confirmarPagoEnPedidos(pago.getPedidoId(), token);
            }
            case 3 -> {
                pago.setEstado(Pago.EstadoPago.RECHAZADO);
                pagoRepository.save(pago);
                log.warn("[Pagos] Pago RECHAZADO: pedidoId={}", pago.getPedidoId());
                notificarPagoFallido(pago.getPedidoId(), "PAGO_RECHAZADO");
            }
            case 4 -> {
                pago.setEstado(Pago.EstadoPago.ANULADO);
                pagoRepository.save(pago);
                log.warn("[Pagos] Pago ANULADO: pedidoId={}", pago.getPedidoId());
                notificarPagoFallido(pago.getPedidoId(), "PAGO_ANULADO");
            }
            default -> log.info("[Pagos] Pago aún PENDIENTE (status={})", estado.getStatus());
        }
    }

    // Notificar a ms-pedidos que el pago fue exitoso
    @Async
    public void confirmarPagoEnPedidos(Long pedidoId, String token) {
        try {
            String url = pedidosUrl + "/pedidos/" + pedidoId + "/confirmar-pago?token=" + token;
            restTemplate.postForEntity(url, null, String.class);
            log.info("[Pagos] ms-pedidos notificado: pedidoId={}", pedidoId);
        } catch (Exception e) {
            log.error("[Pagos] Error al notificar ms-pedidos: {}", e.getMessage());
        }
    }

    // Notificar a ms-pedidos que el pago falló 
    @Async
    public void notificarPagoFallido(Long pedidoId, String nuevoEstado) {
        try {
            String url = pedidosUrl + "/pedidos/" + pedidoId + "/pago-fallido?estado=" + nuevoEstado;
            restTemplate.postForEntity(url, null, String.class);
        } catch (Exception e) {
            log.error("[Pagos] Error al notificar fallo a ms-pedidos: {}", e.getMessage());
        }
    }

    // Consultas
    public Optional<Pago> buscarPorId(Long id) {
        return pagoRepository.findById(id);
    }

    public Optional<Pago> buscarPorToken(String token) {
        return pagoRepository.findByFlowToken(token);
    }

    /**
     * Llamado internamente por api-gateway (PUT /pagos/interno/por-email/
     * {email}/anonimizar) — derecho de cancelación ARCO+ (arco-cancelacion-
     * oposicion.md §4.1/§6.4). Primer endpoint /interno/** de este servicio
     * — la única forma de que quede a salvo de IDOR es la exclusión de ruta
     * en GatewayConfig.pagosRoute(), no este método (ver diseño §7 A01).
     * 0 filas afectadas es un resultado legítimo (cuenta sin pagos), no un error.
     */
    @Transactional
    public int anonimizarPorEmail(String email) {
        int cantidad = pagoRepository.anonimizarPorEmail(email, "USUARIO_ELIMINADO");
        log.info("[ARCO+] Pagos anonimizados por cancelación — cantidad={}", cantidad);
        return cantidad;
    }
}
