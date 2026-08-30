package com.smartlogix.pedidos.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.saga.steps.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock private SagaEstadoRepository sagaRepo;
    @Mock private ObjectMapper         mapper;
    @Mock private ReserveStockStep     step1;
    @Mock private CreateOrderStep      step2;
    @Mock private CreateShipmentStep   step3;
    @Mock private NotifyStep           step4;
    @InjectMocks private SagaOrchestrator orchestrator;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void ejecutar_conCorrelationIdEnMdc_loPersisteEnElEstadoInicial()
            throws SagaStepException {

        // ARRANGE — observability-correlation-ids.md §5.5/criterio de
        // aceptación 7: SagaOrchestrator corre síncrono en el hilo del
        // servlet, así que MDC.get() es confiable aquí (sin el hazard de
        // Retry.backoff() de CancelacionController)
        MDC.put("correlationId", "saga-cid-123");
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setTotal(300.0);
        req.setProductoId(1L);
        req.setCantidad(2);

        when(mapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(java.util.Map.of());
        when(sagaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(step1).execute(any());
        doNothing().when(step2).execute(any());
        doNothing().when(step3).execute(any());
        doNothing().when(step4).execute(any());

        // ACT
        orchestrator.ejecutar(req);

        // ASSERT
        ArgumentCaptor<SagaEstado> captor = ArgumentCaptor.forClass(SagaEstado.class);
        verify(sagaRepo, atLeastOnce()).save(captor.capture());
        assertEquals("saga-cid-123", captor.getAllValues().get(0).getCorrelationId());
    }

    @Test
    void ejecutar_todosLosPasosExitosos_persisteCompletadaYRetornaExito()
            throws SagaStepException {

        // ARRANGE
        // Preparar la solicitud de pedido
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setUserEmail("saga@test.cl");
        req.setTotal(300.0);
        req.setProductoId(1L);
        req.setCantidad(2);

        // Simular que el mapper convierte el request a mapa (para el payload JSONB)
        when(mapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(java.util.Map.of("userId","1"));

        // Simular que el repositorio guarda el estado correctamente
        when(sagaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simular que los 4 pasos se ejecutan sin excepción
        // y que el paso 2 asigna pedidoId al contexto
        doAnswer(inv -> {
            SagaContext ctx = inv.getArgument(0);
            ctx.setPedidoId(99L);
            return null;
        }).when(step2).execute(any(SagaContext.class));

        doAnswer(inv -> {
            SagaContext ctx = inv.getArgument(0);
            ctx.setEnvioId(77L);
            return null;
        }).when(step3).execute(any(SagaContext.class));

        // Los demás pasos no hacen nada (comportamiento por defecto de doNothing)
        doNothing().when(step1).execute(any());
        doNothing().when(step4).execute(any());

        // ACT
        SagaResultado resultado = orchestrator.ejecutar(req);

        // ASSERT
        // La saga debe ser exitosa
        assertTrue(resultado.isExitoso());

        // Deben estar presentes los IDs generados
        assertEquals(99L, resultado.getPedidoId());
        assertEquals(77L, resultado.getEnvioId());

        // El repositorio fue llamado para persistir el estado
        // (al menos una vez para COMPLETADA)
        ArgumentCaptor<SagaEstado> captor = ArgumentCaptor.forClass(SagaEstado.class);
        verify(sagaRepo, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
            .anyMatch(e -> SagaEstado.EstadoSaga.COMPLETADA.equals(e.getEstado())));
    }

    @Test
    void ejecutar_fallaPaso3_compensaPaso2YPaso1EnOrdenInverso()
            throws SagaStepException {

        // ARRANGE
        CreatePedidoRequest req = new CreatePedidoRequest();
        req.setUserId(1L);
        req.setTotal(999.0);
        req.setProductoId(1L);
        req.setCantidad(1);

        when(mapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(java.util.Map.of());
        when(sagaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Paso 1 y 2 se ejecutan bien; el paso 2 asigna pedidoId
        doNothing().when(step1).execute(any());
        doAnswer(inv -> {
            SagaContext ctx = inv.getArgument(0);
            ctx.setPedidoId(50L);
            return null;
        }).when(step2).execute(any(SagaContext.class));

        // Paso 3 lanza SagaStepException (ms-envios caído)
        doThrow(new SagaStepException("CREAR_ENVIO", "ms-envios no responde"))
            .when(step3).execute(any());

        // Las compensaciones no lanzan excepción
        doNothing().when(step1).compensate(any());
        doNothing().when(step2).compensate(any());

        // ACT
        SagaResultado resultado = orchestrator.ejecutar(req);

        // ASSERT
        // La saga debe reportar fallo
        assertFalse(resultado.isExitoso());
        assertNotNull(resultado.getError());

        // Las compensaciones del paso 2 y paso 1 deben haberse ejecutado
        verify(step2, times(1)).compensate(any(SagaContext.class));
        verify(step1, times(1)).compensate(any(SagaContext.class));

        // El paso 4 (notif) no debe haber ejecutado ni su compensación
        verify(step4, never()).compensate(any());

        // El estado FALLIDA debe haberse persistido
        ArgumentCaptor<SagaEstado> captor = ArgumentCaptor.forClass(SagaEstado.class);
        verify(sagaRepo, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
            .anyMatch(e -> SagaEstado.EstadoSaga.FALLIDA.equals(e.getEstado())));
    }
}
