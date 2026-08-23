package com.tienda.pos.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void calculatesProfitAndLowStock() {
        Product product = new Product();
        product.setPurchaseCost(new BigDecimal("14.00"));
        product.setSalePrice(new BigDecimal("20.00"));
        product.setCurrentStock(new BigDecimal("4.000"));
        product.setMinimumStock(new BigDecimal("6.000"));

        assertThat(product.unitProfit()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(product.marginPercent()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(product.hasLowStock()).isTrue();
    }
}
