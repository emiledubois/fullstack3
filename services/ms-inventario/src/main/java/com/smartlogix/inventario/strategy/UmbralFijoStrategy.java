package com.smartlogix.inventario.strategy;

import com.smartlogix.inventario.model.Producto;
import org.springframework.stereotype.Component;

/**
 * ESTRATEGIA CONCRETA 1 — Umbral Fijo
 *
 * Algoritmo: alerta cuando stockActual < umbralMinimo.
 * Este era el comportamiento hardcodeado original en ProductService.
 * Ahora está aislado aquí y es intercambiable.
 *
 * 
 */
@Component("umbralFijo")   // Spring registra este bean con este nombre
public class UmbralFijoStrategy implements AlertaStockStrategy {

    @Override
    public boolean esAlerta(Producto producto) {
        // Algoritmo original: alerta si stock actual está por debajo del mínimo
        return producto.getStockActual() < producto.getUmbralMinimo();
    }

    @Override
    public String getNombre() {
        return "umbral-fijo";
    }

    @Override
    public String getDescripcion() {
        return "Alerta cuando stock < umbralMinimo configurado en el producto";
    }
}
