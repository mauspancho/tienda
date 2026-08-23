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
}
