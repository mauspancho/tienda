package com.tienda.pos.sale;

import com.tienda.pos.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleServiceTest {

    @Test
    void calculatesChange() {
        SaleService service = new SaleService(null, null, null, null, null, null, null);

        assertThat(service.calculateChange(new BigDecimal("103"), new BigDecimal("200")))
                .isEqualByComparingTo(new BigDecimal("97.00"));
        assertThatThrownBy(() -> service.calculateChange(new BigDecimal("103"), new BigDecimal("100")))
                .isInstanceOf(DomainException.class);
    }
}
