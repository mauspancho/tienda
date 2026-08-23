package com.tienda.pos.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyUtilsTest {

    @Test
    void calculatesMargin() {
        assertThat(MoneyUtils.marginPercent(new BigDecimal("20"), new BigDecimal("14")))
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }
}
