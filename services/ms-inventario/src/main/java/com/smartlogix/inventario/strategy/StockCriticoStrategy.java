package com.smartlogix.inventario.strategy;

import com.smartlogix.inventario.model.Producto;
import org.springframework.stereotype.Component;

/**
 * ESTRATEGIA CONCRETA 3 — Stock Crítico (cero unidades)
 *
 * Algoritmo: alerta SOLO cuando no hay absolutamente ningún stock.
 * Una PYME que gestiona productos de alta rotación y alta disponibilidad
 * prefiere que solo se alerte en situaciones realmente críticas.
 *
 * 
 * 
 */
@Component("critico")
public class StockCriticoStrategy implements AlertaStockStrategy {

    @Override
    public boolean esAlerta(Producto producto) {
        // Algoritmo: alerta solo si el stock llegó a cero
        return producto.getStockActual() == 0;
    }

    @Override
    public String getNombre() {
        return "critico";
    }

    @Override
    public String getDescripcion() {
        return "Alerta solo cuando stock = 0 (sin unidades disponibles)";
    }
}
