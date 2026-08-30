package com.smartlogix.pagos.service;

import com.smartlogix.pagos.dto.CrearPagoRequest;
import com.smartlogix.pagos.dto.CrearPagoResponse;
import com.smartlogix.pagos.model.Pago;
import com.smartlogix.pagos.repository.PagoRepository;
import com.smartlogix.pagos.security.CorrelationIdInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private FlowService    flowService;
    @InjectMocks private PagoService pagoService;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void iniciarPago_flowRespondeOk_retornaUrlDeCheckout() {

        // ARRANGE
        // Inyectar las URLs de configuración (normalmente vienen de @Value)
        ReflectionTestUtils.setField(pagoService, "urlConfirmation",
            "https://test.ngrok.app/pagos/webhook/flow");
        ReflectionTestUtils.setField(pagoService, "urlReturn",
            "http://localhost:5173/pago-resultado");
        ReflectionTestUtils.setField(pagoService, "pedidosUrl",
            "http://ms-pedidos:8083");

        // Preparar la solicitud de pago
        CrearPagoRequest req = new CrearPagoRequest();
        req.setPedidoId(1L);
        req.setMonto(5000.0);
        req.setEmail("cliente@test.cl");
        req.setDescripcion("Pedido SmartLogix #1");

        // Simular que FlowService crea la orden exitosamente
        FlowService.FlowCreateResponse flowResp = new FlowService.FlowCreateResponse();
        flowResp.setUrl("https://sandbox.flow.cl/app/web/pay.php");
        flowResp.setToken("TOKEN_FLOW_123");
        flowResp.setFlowOrder(999L);
        when(flowService.crearOrden(any(), any(), any(), any(), any(), any()))
            .thenReturn(flowResp);

        // Simular que el repositorio guarda el pago y retorna el objeto
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        // ACT
        CrearPagoResponse respuesta = pagoService.iniciarPago(req);

        // ASSERT
        // La URL de pago debe contener la URL de Flow y el token
        assertNotNull(respuesta.getUrlPago());
        assertTrue(respuesta.getUrlPago().contains("sandbox.flow.cl"));
        assertTrue(respuesta.getUrlPago().contains("TOKEN_FLOW_123"));

        // El estado inicial debe ser INICIADO
        assertEquals("INICIADO", respuesta.getEstado());

        // El pagoId debe haber sido asignado
        assertNotNull(respuesta.getPagoId());

        // El repositorio debe haber sido llamado para persistir
        verify(pagoRepository, times(1)).save(any(Pago.class));
    }

    @Test
    void procesarWebhook_status2_actualizaPagoAPagado() {

        // ARRANGE
        ReflectionTestUtils.setField(pagoService, "pedidosUrl", "http://ms-pedidos:8083");
        ReflectionTestUtils.setField(pagoService, "verifySignature", true);

        String token = "TOKEN_OK_456";
        String firma = "firma_valida";

        // Simular que la firma del webhook es válida
        when(flowService.verificarFirmaWebhook(token, firma)).thenReturn(true);

        // Simular que getStatus retorna status 2 (pago exitoso)
        FlowService.FlowStatusResponse statusResp = new FlowService.FlowStatusResponse();
        statusResp.setStatus(2);
        statusResp.setFlowOrder(555L);
        statusResp.setAmount(5000.0);
        when(flowService.obtenerEstado(token)).thenReturn(statusResp);

        // Simular que el pago existe en la BD con ese token
        Pago pagoExistente = new Pago();
        pagoExistente.setId(1L);
        pagoExistente.setPedidoId(10L);
        pagoExistente.setFlowToken(token);
        pagoExistente.setEstado(Pago.EstadoPago.INICIADO);
        when(pagoRepository.findByFlowToken(token))
            .thenReturn(java.util.Optional.of(pagoExistente));
        when(pagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ACT
        pagoService.procesarWebhook(token, firma);

        // ASSERT
        // El pago debe haber sido actualizado a PAGADO
        assertEquals(Pago.EstadoPago.PAGADO, pagoExistente.getEstado());

        // La fecha de confirmación debe haber sido registrada
        assertNotNull(pagoExistente.getConfirmadoEn());

        // El repositorio debe haber guardado el pago actualizado
        verify(pagoRepository, times(1)).save(pagoExistente);
    }


    @Test
    void procesarWebhook_status3_actualizaARechazadoSinDispararSaga() {

        // ARRANGE
        ReflectionTestUtils.setField(pagoService, "pedidosUrl", "http://ms-pedidos:8083");

        String token = "TOKEN_RECHAZADO_789";

        // Sin firma (Flow sandbox puede no enviarla)
        when(flowService.obtenerEstado(token)).thenReturn(new FlowService.FlowStatusResponse() {{
            setStatus(3);
            setFlowOrder(777L);
        }});

        Pago pago = new Pago();
        pago.setId(2L);
        pago.setPedidoId(20L);
        pago.setFlowToken(token);
        pago.setEstado(Pago.EstadoPago.INICIADO);
        when(pagoRepository.findByFlowToken(token))
            .thenReturn(java.util.Optional.of(pago));
        when(pagoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ACT
        pagoService.procesarWebhook(token, null);

        // ASSERT
        // El pago debe actualizarse a RECHAZADO
        assertEquals(Pago.EstadoPago.RECHAZADO, pago.getEstado());

        // El repositorio debe haber guardado el cambio
        verify(pagoRepository, times(1)).save(pago);
    }

    @Test
    void confirmarPagoEnPedidos_conCorrelationId_loRestauraEnMdcYLoPropagaAlRestTemplate() {

        // ARRANGE — observability-correlation-ids.md §5.3: confirmarPagoEnPedidos
        // es @Async (arranca en un hilo del pool de tareas, sin el MDC del
        // hilo que atendió el webhook), así que debe restaurar el MDC a
        // partir del parámetro recibido para que CorrelationIdInterceptor
        // (que lee MDC.get() en ese mismo hilo, sin más saltos) lo fije en
        // la petición saliente.
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new CorrelationIdInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(pagoService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(pagoService, "pedidosUrl", "http://ms-pedidos:8083");

        server.expect(requestTo("http://ms-pedidos:8083/pedidos/10/confirmar-pago?token=tok-1"))
            .andExpect(header("X-Correlation-Id", "cid-webhook-1"))
            .andRespond(withSuccess());

        // ACT
        pagoService.confirmarPagoEnPedidos(10L, "tok-1", "cid-webhook-1");

        // ASSERT
        server.verify();
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void notificarPagoFallido_conCorrelationId_loRestauraEnMdcYLoPropagaAlRestTemplate() {

        // ARRANGE — mismo hazard que confirmarPagoEnPedidos: notificarPagoFallido
        // también es @Async
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new CorrelationIdInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(pagoService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(pagoService, "pedidosUrl", "http://ms-pedidos:8083");

        server.expect(requestTo("http://ms-pedidos:8083/pedidos/20/pago-fallido?estado=PAGO_RECHAZADO"))
            .andExpect(header("X-Correlation-Id", "cid-webhook-2"))
            .andRespond(withSuccess());

        // ACT
        pagoService.notificarPagoFallido(20L, "PAGO_RECHAZADO", "cid-webhook-2");

        // ASSERT
        server.verify();
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void anonimizarPorEmail_cuentaConPagos_retornaCantidadDeFilasAfectadas() {

        // ARRANGE — derecho de cancelación ARCO+ (arco-cancelacion-oposicion.md §4.1)
        when(pagoRepository.anonimizarPorEmail("dueña@pyme.cl", "USUARIO_ELIMINADO")).thenReturn(1);

        // ACT
        int cantidad = pagoService.anonimizarPorEmail("dueña@pyme.cl");

        // ASSERT
        assertEquals(1, cantidad);
        verify(pagoRepository).anonimizarPorEmail("dueña@pyme.cl", "USUARIO_ELIMINADO");
    }

    @Test
    void anonimizarPorEmail_cuentaSinPagos_retornaCeroSinLanzarExcepcion() {

        // ARRANGE — 0 filas afectadas es un resultado legítimo, no un error
        when(pagoRepository.anonimizarPorEmail("nadie@pyme.cl", "USUARIO_ELIMINADO")).thenReturn(0);

        // ACT
        int cantidad = pagoService.anonimizarPorEmail("nadie@pyme.cl");

        // ASSERT
        assertEquals(0, cantidad);
    }
}
