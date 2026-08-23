package com.tienda.pos.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServiceTest {

    @Test
    void calculatesEan13CheckDigit() {
        assertThat(ProductService.ean13CheckDigit("750105530000")).isEqualTo(6);
    }
}
