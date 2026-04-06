package com.ecommerce.product.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("percentageDiscount")
public class PercentageDiscountStrategy implements DiscountStrategy {

    @Value("${discount.percentage:10}")
    private double percentage;

    @Override
    public double apply(double price) {
        return price * (1 - percentage / 100);
    }

    @Override
    public String getDescription() {
        return percentage + "% de descuento";
    }
}
