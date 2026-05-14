package com.smartlogix.inventario.strategy;

import com.smartlogix.inventario.model.Producto;
import org.springframework.stereotype.Component;

/**
 * ESTRATEGIA CONCRETA 2 — Por Porcentaje
 *
 * Algoritmo: alerta cuando el stock está por debajo del 150% del umbral.
 * Permite anticipar el desabastecimiento antes de que sea crítico.
 * Una PYME con tiempos de reabastecimiento largos prefiere esta estrategia.
 *
 * 
 * 
 */
@Component("porcentaje")   // Spring registra con nombre diferente al anterior
public class PorcentajeStrategy implements AlertaStockStrategy {

    // Factor configurable: 1.5 = alerta al 150% del umbral mínimo
    private static final double FACTOR = 1.5;

    @Override
    public boolean esAlerta(Producto producto) {
        // Algoritmo diferente: alerta más temprana que UmbralFijo
        double umbralExtendido = producto.getUmbralMinimo() * FACTOR;
        return producto.getStockActual() < umbralExtendido;
    }

    @Override
    public String getNombre() {
        return "porcentaje";
    }

    @Override
    public String getDescripcion() {
        return "Alerta temprana: stock < umbralMinimo * 1.5 (anticipa desabastecimiento)";
    }
}
