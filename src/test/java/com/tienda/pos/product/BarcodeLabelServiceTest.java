package com.tienda.pos.product;

import com.tienda.pos.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeLabelServiceTest {

    private final BarcodeLabelService service = new BarcodeLabelService();

    @Test
    void rendersCode128SvgForBarcode() {
        String svg = service.code128Svg("7501372895933");

        assertThat(svg).startsWith("<svg");
        assertThat(svg).contains("<rect");
        assertThat(svg).contains("viewBox");
    }

    @Test
    void rejectsBlankBarcode() {
        assertThatThrownBy(() -> service.code128Svg(" "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("código de barras");
    }
}
