package com.ecommerce.product.strategy;

import org.springframework.stereotype.Component;

@Component("noDiscount")
public class NoDiscountStrategy implements DiscountStrategy {
    @Override public double apply(double price) { return price; }
    @Override public String getDescription() { return "Sin descuento"; }
}
