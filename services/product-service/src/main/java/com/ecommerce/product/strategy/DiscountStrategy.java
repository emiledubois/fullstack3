package com.ecommerce.product.strategy;

// Strategy Pattern — interfaz que define el contrato
// Cada implementación es un algoritmo de descuento intercambiable
public interface DiscountStrategy {
    double apply(double originalPrice);
    String getDescription();
}
