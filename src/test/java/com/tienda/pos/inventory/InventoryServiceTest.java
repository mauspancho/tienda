package com.tienda.pos.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceTest {

    @Test
    void signsInventoryQuantities() {
        InventoryService service = new InventoryService(null, null);

        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_IN, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("3"));
        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_OUT, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("-3"));
        assertThat(service.signedQuantity(InventoryMovementType.SALE, new BigDecimal("2")))
                .isEqualByComparingTo(new BigDecimal("-2"));
    }

    @Test
    void calculatesWeightedAverageCostForIncomingStock() {
        InventoryService service = new InventoryService(null, null);

        BigDecimal average = service.weightedAverageCost(
                new BigDecimal("10.000"),
                new BigDecimal("17.00"),
                new BigDecimal("10.000"),
                new BigDecimal("14.80")
        );

        assertThat(average).isEqualByComparingTo(new BigDecimal("15.90"));
    }
}

