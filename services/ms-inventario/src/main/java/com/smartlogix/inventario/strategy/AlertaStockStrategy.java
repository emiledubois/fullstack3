package com.smartlogix.inventario.strategy;

import com.smartlogix.inventario.model.Producto;

/**
 * PATRÓN STRATEGY — Interfaz Strategy 
 *
 * Define el contrato común para TODAS las estrategias de detección
 * de alertas de stock. 
 *
 * El Contexto (AlertaService) trabaja SOLO con esta interfaz,
 * nunca con las implementaciones concretas.
 */
public interface AlertaStockStrategy {

    /**
     * Algoritmo de detección: ¿este producto necesita una alerta?
     * Cada implementación concreta responde esto de forma diferente.
     *
     * @param producto el producto a evaluar
     * @return true si el producto requiere alerta de stock
     */
    boolean esAlerta(Producto producto);

    /**
     * Nombre descriptivo de la estrategia (para logs y respuestas API).
     */
    String getNombre();

    /**
     * Descripción legible para el usuario de la PYME.
     */
    String getDescripcion();
}
