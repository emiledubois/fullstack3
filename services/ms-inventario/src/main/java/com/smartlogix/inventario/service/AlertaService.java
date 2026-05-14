package com.smartlogix.inventario.service;

import com.smartlogix.inventario.dto.AlertaResponseDTO;
import com.smartlogix.inventario.dto.ProductDTO;
import com.smartlogix.inventario.model.Producto;
import com.smartlogix.inventario.repository.InventarioRepository;
import com.smartlogix.inventario.strategy.AlertaStockStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AlertaService {

    // Campo privado: referencia a la estrategia (exactamente como el diagrama del PDF)
    private AlertaStockStrategy strategy;

    private final InventarioRepository inventarioRepository;

    // Spring inyecta todas las estrategias disponibles en un Map<nombre, bean>
    // Map key = nombre del @Component, value = la instancia de la estrategia
    private final Map<String, AlertaStockStrategy> strategiesMap;

    public AlertaService(InventarioRepository inventarioRepository,
                         Map<String, AlertaStockStrategy> strategiesMap) {
        this.inventarioRepository = inventarioRepository;
        this.strategiesMap = strategiesMap;
        // Estrategia por defecto: umbral fijo (comportamiento original)
        this.strategy = strategiesMap.get("umbralFijo");
    }

    /**
     * Del PDF: "La clase contexto expone un modificador set que permite
     * a los clientes sustituir la estrategia asociada al contexto
     * durante el tiempo de ejecución."
     *
     * Permite cambiar la estrategia en tiempo de ejecución.
     */
    public void setStrategy(String nombreEstrategia) {
        AlertaStockStrategy nueva = strategiesMap.get(nombreEstrategia);
        if (nueva == null) {
            throw new IllegalArgumentException(
                "Estrategia desconocida: " + nombreEstrategia +
                ". Disponibles: " + strategiesMap.keySet());
        }
        this.strategy = nueva;
        log.info("[Strategy] Estrategia de alerta cambiada a: {}", strategy.getNombre());
    }

    /**
     * Obtener alertas usando la estrategia actual.
     *
     * Del PDF: "El contexto invoca el método de ejecución en el objeto de
     * estrategia vinculado cada vez que necesita ejecutar el algoritmo.
     * La clase contexto no sabe con qué tipo de estrategia funciona."
     */
    public AlertaResponseDTO getAlertas() {
        List<Producto> todos = inventarioRepository.findAll();

        // Delegamos a la estrategia: strategy.esAlerta(producto)
        // El contexto no sabe cómo se evalúa — lo decide la estrategia
        List<ProductDTO> alertas = todos.stream()
            .filter(p -> strategy.esAlerta(p))   // ← delegación al Strategy
            .map(ProductDTO::from)
            .collect(Collectors.toList());

        log.info("[Strategy] Estrategia={}, Total={}, Alertas={}",
                 strategy.getNombre(), todos.size(), alertas.size());

        return AlertaResponseDTO.builder()
            .estrategia(strategy.getNombre())
            .descripcionEstrategia(strategy.getDescripcion())
            .totalProductos(todos.size())
            .alertas(alertas)
            .build();
    }

    /**
     * Listar estrategias disponibles.
     */
    public List<String> getEstrategiasDisponibles() {
        return strategiesMap.values().stream()
            .map(s -> s.getNombre() + ": " + s.getDescripcion())
            .collect(Collectors.toList());
    }
}
