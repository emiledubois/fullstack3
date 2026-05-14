package com.smartlogix.pedidos.facade;

import com.smartlogix.pedidos.client.InventarioClient;
import com.smartlogix.pedidos.dto.CreatePedidoRequest;
import com.smartlogix.pedidos.dto.OrderDTO;
import com.smartlogix.pedidos.event.PedidoAprobadoEvent;
import com.smartlogix.pedidos.factory.PedidoFactory;
import com.smartlogix.pedidos.model.Pedido;
import com.smartlogix.pedidos.repository.OrderRepository;
import com.smartlogix.pedidos.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PATRÓN FACADE — Fachada de Logística (Pedidos)
 *
 * Propósito: proporcionar una interfaz SIMPLE para el proceso complejo
 * de crear un pedido en SmartLogix.
 *
 * Del PDF (Facade): "Una fachada es una clase que proporciona una interfaz
 * simple a un subsistema complejo que contiene muchas partes móviles."
 *
 * Subsistemas que esta fachada coordina (y que el Controller YA NO ve):
 *   1. InventarioClient   — verificación de stock con Circuit Breaker
 *   2. PedidoFactory      — creación del tipo correcto de pedido
 *   3. OrderRepository    — persistencia en base de datos
 *   4. EventPublisher     — notificación asíncrona Observer Pattern
 *
 * Analogía del PDF: el operador telefónico que el cliente llama.
 * El operador coordina Almacén, Empaquetado y Entrega internamente.
 *
 * Diferencia con OrderService:
 * OrderService = subsistema (conoce la lógica de negocio de pedidos)
 * LogisticaFacade = fachada (coordina subsistemas para el caso de uso completo)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogisticaFacade {

    // Los subsistemas que esta fachada coordina.
    // Del PDF: "La fachada mantiene referencias a los objetos del subsistema".
    // Los subsistemas NO saben que la fachada existe.
    private final InventarioClient         inventarioClient;   // subsistema 1
    private final PedidoFactory            pedidoFactory;      // subsistema 2
    private final OrderRepository          orderRepository;    // subsistema 3
    private final ApplicationEventPublisher eventPublisher;    // subsistema 4
    private final OrderService             orderService;       // subsistema adicional

    // ─────────────────────────────────────────────────────────────
    // OPERACIONES DE LA FACHADA — interfaz simplificada para el cliente
    // ─────────────────────────────────────────────────────────────

    /**
     * Crea un pedido completo coordinando todos los subsistemas.
     *
     * Para el OrderController, esto es UNA sola llamada.
     * Internamente la fachada orquesta 4 pasos en el orden correcto.
     *
     * Del PDF: "La fachada sabe a dónde dirigir la petición del cliente
     * y cómo operar todas las partes móviles."
     */
    public OrderDTO procesarCreacionPedido(CreatePedidoRequest req) {
        log.info("[Facade] Iniciando creación de pedido para usuario: {}", req.getUserId());

        // PASO 1: Subsistema InventarioClient
        // Verificar stock usando el Circuit Breaker ya implementado.
        // El controller no necesita saber que existe un InventarioClient.
        if (req.getProductoId() != null) {
            int qty = req.getCantidad() != null ? req.getCantidad() : 1;
            log.info("[Facade] Verificando stock: productoId={}, cantidad={}", req.getProductoId(), qty);
            Boolean hayStock = inventarioClient.verificarStock(req.getProductoId(), qty);
            if (Boolean.FALSE.equals(hayStock)) {
                log.warn("[Facade] Stock insuficiente para productoId={}", req.getProductoId());
                throw new RuntimeException("Stock insuficiente o servicio de inventario no disponible");
            }
        }

        // PASO 2: Subsistema PedidoFactory
        // Crear el objeto Pedido del tipo correcto (NACIONAL o INTERNACIONAL).
        // El controller no sabe que existe PedidoFactory.
        log.info("[Facade] Creando pedido tipo: {}", req.getTipoPedido());
        Pedido pedido = pedidoFactory.crearPedido(req);

        // PASO 3: Subsistema OrderRepository
        // Persistir el pedido en la base de datos.
        Pedido saved = orderRepository.save(pedido);
        log.info("[Facade] Pedido persistido: id={}", saved.getId());

        // PASO 4: Subsistema ApplicationEventPublisher (Observer Pattern)
        // Publicar el evento para que notification-service notifique al cliente.
        // El controller no sabe nada del Observer Pattern.
        eventPublisher.publishEvent(
            new PedidoAprobadoEvent(this, saved.getId(),
                saved.getUserEmail(), saved.getTotal(), saved.getTipoPedido()));
        log.info("[Facade] Evento PedidoAprobadoEvent publicado");

        log.info("[Facade] Pedido {} creado exitosamente", saved.getId());
        return OrderDTO.from(saved);
    }

    /**
     * Obtener todos los pedidos — delega al subsistema OrderService.
     * La fachada simplifica también las consultas.
     */
    public List<OrderDTO> listarPedidos() {
        return orderService.getAllOrders();
    }

    /**
     * Obtener pedido por ID.
     */
    public OrderDTO obtenerPedido(Long id) {
        return orderService.getOrderById(id);
    }
}
