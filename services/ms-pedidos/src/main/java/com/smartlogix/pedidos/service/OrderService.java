package com.smartlogix.pedidos.service;

import com.smartlogix.pedidos.client.InventarioClient;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.event.PedidoAprobadoEvent;
import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import com.smartlogix.pedidos.saga.SagaOrchestrator;
import com.smartlogix.pedidos.saga.SagaResultado;
import com.smartlogix.pedidos.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InventarioClient inventarioClient;
    private final PedidoFactory pedidoFactory;
    private final SagaOrchestrator sagaOrchestrator;

    public OrderDTO createOrder(CreatePedidoRequest req) {
        // Circuit Breaker: verificar stock en ms-inventario
        if (req.getProductoId() != null) {
            int qty = req.getCantidad() != null ? req.getCantidad() : 1;
            Boolean hayStock = inventarioClient.verificarStock(req.getProductoId(), qty);
            if (Boolean.FALSE.equals(hayStock)) {
                throw new RuntimeException("Stock insuficiente o servicio de inventario no disponible");
            }
        }

        // Factory Method: crea PedidoNacional o PedidoInternacional
        Pedido pedido = pedidoFactory.crearPedido(req);
        Pedido saved = orderRepository.save(pedido);

        // Observer Pattern: publicar evento → notification-service
        eventPublisher.publishEvent(
            new PedidoAprobadoEvent(this, saved.getId(),
                saved.getUserEmail(), saved.getTotal(), saved.getTipoPedido()));

        log.info("Pedido creado: id={} tipo={}", saved.getId(), saved.getTipoPedido());
        return OrderDTO.from(saved);
    }

    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream().map(OrderDTO::from).collect(Collectors.toList());
    }

    public OrderDTO getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(OrderDTO::from)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + id));
    }

    /**
     * Llamado cuando ms-pagos confirma el pago de Flow.
     * 1. Cambia el estado del pedido a PAGADO.
     * 2. Dispara la Saga original (4 pasos: stock → pedido → envío → notif).
     */
    @Transactional
    public void confirmarPagoYDispararSaga(Long pedidoId, String flowToken) {
        Pedido pedido = orderRepository.findById(pedidoId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        pedido.setStatus("PAGADO");
        pedido.setObservaciones("Pago Flow confirmado. Token: " + flowToken);
        orderRepository.save(pedido);

        log.info("[Pedidos] Pago confirmado para pedido={}, disparando Saga", pedidoId);

        // Construir el request para la Saga a partir del pedido ya existente
        CreatePedidoRequest sagaReq = new CreatePedidoRequest();
		sagaReq.setUserId(pedido.getUserId());
		sagaReq.setUserEmail(pedido.getUserEmail());
		sagaReq.setClienteNombre(pedido.getClienteNombre());
		sagaReq.setTotal(pedido.getTotal());
		sagaReq.setTipoPedido(pedido.getTipoPedido());
		sagaReq.setDestino(pedido.getDestino());
		sagaReq.setProductoId(pedido.getProductoId());
		sagaReq.setCantidad(pedido.getCantidad());

        // Disparar la Saga — el orquestador maneja las 4 etapas y compensaciones
        SagaResultado resultado = sagaOrchestrator.ejecutar(sagaReq);
        log.info("[Pedidos] Saga disparada: sagaId={}, exitoso={}",
            resultado.getSagaId(), resultado.isExitoso());
    }

    @Transactional
    public void marcarPagoFallido(Long pedidoId, String estadoFlow) {
        orderRepository.findById(pedidoId).ifPresent(p -> {
            p.setStatus(estadoFlow);
            p.setObservaciones("Pago Flow: " + estadoFlow);
            orderRepository.save(p);
            log.warn("[Pedidos] Pedido {} marcado como {}", pedidoId, estadoFlow);
        });
    }
}
