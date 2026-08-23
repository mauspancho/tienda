package com.tienda.pos.cash;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashServiceTest {

    @Test
    void calculatesCashDifference() {
        CashService service = new CashService(null, null, null);

        assertThat(service.difference(new BigDecimal("3300"), new BigDecimal("3280")))
                .isEqualByComparingTo(new BigDecimal("-20.00"));
    }
}
