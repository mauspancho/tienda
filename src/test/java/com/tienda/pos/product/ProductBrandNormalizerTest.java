package com.tienda.pos.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBrandNormalizerTest {

    private final ProductBrandNormalizer normalizer = new ProductBrandNormalizer();

    @Test
    void normalizesCaseWhitespaceAndMultipleWords() {
        assertThat(normalizer.normalize("  GAMESA  ")).isEqualTo("Gamesa");
        assertThat(normalizer.normalize("LA   MODERNA")).isEqualTo("La Moderna");
        assertThat(normalizer.normalize("de la rosa")).isEqualTo("De La Rosa");
    }

    @Test
    void preservesUnicodeAccentsNumbersAndCommercialSeparators() {
        assertThat(normalizer.normalize("LA COSTEÑA")).isEqualTo("La Costeña");
        assertThat(normalizer.normalize("coca-cola")).isEqualTo("Coca-Cola");
        assertThat(normalizer.normalize("3M")).isEqualTo("3M");
        assertThat(normalizer.normalize("h&m")).isEqualTo("H&M");
    }

    @Test
    void comparisonDoesNotRemoveAccentsOrUsePartialMatching() {
        assertThat(normalizer.key("  GaMeSa ")).isEqualTo(normalizer.key("Gamesa"));
        assertThat(normalizer.key("Costeña")).isNotEqualTo(normalizer.key("Costena"));
        assertThat(normalizer.key("Games")).isNotEqualTo(normalizer.key("Gamesa"));
    }

    @Test
    void blankValuesBecomeNull() {
        assertThat(normalizer.normalize(" \t ")).isNull();
        assertThat(normalizer.normalize(null)).isNull();
    }
}
