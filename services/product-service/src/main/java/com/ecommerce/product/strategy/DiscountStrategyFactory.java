package com.ecommerce.product.strategy;

import org.springframework.stereotype.Component;
import java.util.Map;

// Factory Method Pattern — selecciona la estrategia según tipo de usuario
// Spring inyecta automáticamente todos los beans que implementan DiscountStrategy
@Component
public class DiscountStrategyFactory {

    private final Map<String, DiscountStrategy> strategies;

    public DiscountStrategyFactory(Map<String, DiscountStrategy> strategies) {
        this.strategies = strategies;
    }

    public DiscountStrategy getStrategy(String userType) {
        return switch (userType != null ? userType.toUpperCase() : "DEFAULT") {
            case "VIP"  -> strategies.get("percentageDiscount");
            default     -> strategies.get("noDiscount");
        };
    }
}
