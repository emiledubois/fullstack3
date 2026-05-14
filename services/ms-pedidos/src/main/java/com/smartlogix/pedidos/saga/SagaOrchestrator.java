package com.smartlogix.pedidos.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.saga.steps.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service @Slf4j
public class SagaOrchestrator {

    private final SagaEstadoRepository sagaRepo;
    private final ObjectMapper          mapper;
    private final List<SagaStep>        steps;

    public SagaOrchestrator(
            SagaEstadoRepository sagaRepo,
            ObjectMapper         mapper,
            ReserveStockStep     step1,
            CreateOrderStep      step2,
            CreateShipmentStep   step3,
            NotifyStep           step4) {
        this.sagaRepo = sagaRepo;
        this.mapper   = mapper;
        // El ORDEN de esta lista define el orden de ejecución.
        // Las compensaciones se ejecutan en orden INVERSO.
        this.steps = List.of(step1, step2, step3, step4);
    }

    @Transactional
    public SagaResultado ejecutar(CreatePedidoRequest request) {
        UUID sagaId = UUID.randomUUID();
        log.info("[Saga {}] INICIADA — userId={}", sagaId, request.getUserId());

        // Persistir estado inicial
        Map<String, Object> payload = mapper.convertValue(request, new TypeReference<>() {});
        SagaEstado estado = SagaEstado.builder()
            .sagaId(sagaId).tipo("CREAR_PEDIDO").pasoActual("INICIADA")
            .estado(SagaEstado.EstadoSaga.INICIADA).payload(payload)
            .build();
        sagaRepo.save(estado);

        SagaContext ctx = SagaContext.builder()
            .sagaId(sagaId).request(request).build();

        // ── FASE DE EJECUCIÓN ──
        int pasoFallido = -1;
        String errorMsg = null;

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            try {
                estado.setPasoActual(step.getName());
                estado.setEstado(SagaEstado.EstadoSaga.EN_PROGRESO);
                sagaRepo.save(estado);

                step.execute(ctx);

                // Actualizar IDs en el estado persistido
                if (ctx.getProductoId()  != null) estado.setStockReservado(true);
                if (ctx.getPedidoId()    != null) estado.setPedidoId(ctx.getPedidoId());
                if (ctx.getEnvioId()     != null) estado.setEnvioId(ctx.getEnvioId());
                sagaRepo.save(estado);

                log.info("[Saga {}] Paso {} COMPLETADO", sagaId, step.getName());
            } catch (SagaStepException e) {
                pasoFallido = i;
                errorMsg = e.getMessage();
                log.error("[Saga {}] Paso {} FALLIDO: {}", sagaId, step.getName(), errorMsg);
                break;
            }
        }

        // ── FASE DE COMPENSACIÓN (si hubo fallo) ──
        if (pasoFallido >= 0) {
            estado.setEstado(SagaEstado.EstadoSaga.COMPENSANDO);
            estado.setUltimoError(errorMsg);
            sagaRepo.save(estado);

            log.warn("[Saga {}] Iniciando compensaciones desde paso {}", sagaId, pasoFallido);

            // Compensar en orden INVERSO desde el paso que falló
            for (int i = pasoFallido; i >= 0; i--) {
                try {
                    SagaStep step = steps.get(i);
                    log.warn("[Saga {}] Compensando paso: {}", sagaId, step.getName());
                    step.compensate(ctx);
                } catch (Exception e) {
                    // Las compensaciones no deben interrumpir el proceso
                    log.error("[Saga {}] Error inesperado en compensación {}: {}",
                             sagaId, steps.get(i).getName(), e.getMessage());
                }
            }

            estado.setEstado(SagaEstado.EstadoSaga.FALLIDA);
            estado.setPasoActual("FALLIDA");
            sagaRepo.save(estado);

            log.warn("[Saga {}] FALLIDA y compensada", sagaId);
            return SagaResultado.fallo(sagaId, errorMsg);
        }

        // ── SAGA COMPLETADA EXITOSAMENTE ──
        estado.setEstado(SagaEstado.EstadoSaga.COMPLETADA);
        estado.setPasoActual("COMPLETADA");
        sagaRepo.save(estado);

        log.info("[Saga {}] COMPLETADA — pedidoId={}, envioId={}",
                 sagaId, ctx.getPedidoId(), ctx.getEnvioId());
        return SagaResultado.exito(sagaId, ctx.getPedidoId(), ctx.getEnvioId());
    }

    public Optional<SagaEstado> consultarEstado(UUID sagaId) {
        return sagaRepo.findById(sagaId);
    }
}
