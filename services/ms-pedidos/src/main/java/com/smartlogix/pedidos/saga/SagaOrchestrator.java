package com.smartlogix.pedidos.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.saga.steps.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class SagaOrchestrator {

    private final SagaEstadoRepository sagaRepo;
    private final ObjectMapper          mapper;
    private final List<SagaStep>        steps;

    public SagaOrchestrator(
            SagaEstadoRepository sagaRepo,
            ObjectMapper mapper,
            ReserveStockStep   step1,
            CreateOrderStep    step2,
            CreateShipmentStep step3,
            NotifyStep         step4
    ) {
        this.sagaRepo = sagaRepo;
        this.mapper   = mapper;
        // El orden importa: primero execute() en este orden,
        // compensate() en orden inverso.
        this.steps = List.of(step1, step2, step3, step4);
    }

    /**
     * Inicia y ejecuta la saga completa.
     * Persistencia de estado en cada transición para tolerancia a fallos.
     */
    @Transactional
    public SagaResultado ejecutar(CreatePedidoRequest request) {
        UUID sagaId = UUID.randomUUID();
        log.info("[Saga {}] INICIADA — userId={}", sagaId, request.getUserId());

        // Persistir estado inicial
        Map<String, Object> payload = mapper.convertValue(request, new TypeReference<>() {});
        SagaEstado estado = SagaEstado.builder()
            .sagaId(sagaId)
            .tipo("CREAR_PEDIDO")
            .pasoActual("INICIADA")
            .estado(SagaEstado.EstadoSaga.INICIADA)
            .payload(payload)
            .build();
        sagaRepo.save(estado);

        // Contexto mutable que viaja por los pasos
        SagaContext ctx = SagaContext.builder()
            .sagaId(sagaId)
            .request(request)
            .build();

        // Ejecutar pasos secuencialmente
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
                if (ctx.getPedidoId()  != null) estado.setPedidoId(ctx.getPedidoId());
                if (ctx.getEnvioId()   != null) estado.setEnvioId(ctx.getEnvioId());
                if (ctx.getProductoId() != null) estado.setStockReservado(true);
                sagaRepo.save(estado);

                log.info("[Saga {}] Paso {} completado", sagaId, step.getName());
            } catch (SagaStepException e) {
                pasoFallido = i;
                errorMsg = e.getMessage();
                log.error("[Saga {}] Fallo en paso {}: {}", sagaId, step.getName(), errorMsg);
                break;
            }
        }

        // Si hubo fallo, compensar en orden inverso
        if (pasoFallido >= 0) {
            estado.setEstado(SagaEstado.EstadoSaga.COMPENSANDO);
            estado.setUltimoError(errorMsg);
            sagaRepo.save(estado);

            log.warn("[Saga {}] Iniciando compensaciones desde paso {}", sagaId, pasoFallido);
            for (int i = pasoFallido; i >= 0; i--) {
                SagaStep step = steps.get(i);
                try {
                    log.warn("[Saga {}] Compensando: {}", sagaId, step.getName());
                    step.compensate(ctx);
                } catch (Exception e) {
                    // Las compensaciones no deben lanzar — ya lo manejan internamente
                    log.error("[Saga {}] Error inesperado en compensación de {}: {}", sagaId, step.getName(), e.getMessage());
                }
            }

            estado.setEstado(SagaEstado.EstadoSaga.FALLIDA);
            estado.setPasoActual("FALLIDA");
            sagaRepo.save(estado);

            return SagaResultado.fallo(sagaId, errorMsg);
        }

        // Saga completada exitosamente
        estado.setEstado(SagaEstado.EstadoSaga.COMPLETADA);
        estado.setPasoActual("COMPLETADA");
        sagaRepo.save(estado);

        log.info("[Saga {}] COMPLETADA — pedidoId={}, envioId={}", sagaId, ctx.getPedidoId(), ctx.getEnvioId());
        return SagaResultado.exito(sagaId, ctx.getPedidoId(), ctx.getEnvioId());
    }
}
