package com.tienda.pos.product;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductBrandServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductBrandService service = new ProductBrandService(repository, new ProductBrandNormalizer());

    @Test
    void reusesCanonicalBrandAndKeepsPartialNamesSeparate() {
        when(repository.findDistinctBrands(10L)).thenReturn(List.of("Gamesa", "Games"));

        assertThat(service.canonicalize(10L, "  GAMESA ")).isEqualTo("Gamesa");
        assertThat(service.canonicalize(10L, "games")).isEqualTo("Games");
        assertThat(service.canonicalize(10L, "gamesa company")).isEqualTo("Gamesa Company");
        verify(repository, org.mockito.Mockito.times(3)).findDistinctBrands(10L);
    }

    @Test
    void suggestionsRequireTwoCharactersPrioritizePrefixesAndStayInTenant() {
        when(repository.findDistinctBrands(22L)).thenReturn(List.of("Moderna", "Modern Life", "Casa Moderna", "La Costeña"));

        assertThat(service.suggest(22L, "l")).isEmpty();
        assertThat(service.suggest(22L, "mo")).containsExactly("Modern Life", "Moderna", "Casa Moderna");
        verify(repository).findDistinctBrands(22L);
    }

    @Test
    void simultaneousCanonicalizationProducesOneDeterministicRepresentation() throws Exception {
        when(repository.findDistinctBrands(7L)).thenReturn(List.of());
        List<String> inputs = List.of("GAMESA", "gamesa", " GaMeSa ", "GAMESA", "gamesa");
        try (var executor = Executors.newFixedThreadPool(inputs.size())) {
            List<Callable<String>> tasks = inputs.stream()
                    .<Callable<String>>map(value -> () -> service.canonicalize(7L, value))
                    .toList();
            assertThat(executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            })).containsOnly("Gamesa");
        }
    }

    @Test
    void migrationReportAndApplyAreIdempotent() {
        Product first = product(1L, "GAMESA");
        Product second = product(2L, " gamesa ");
        Product third = product(3L, "Games");
        when(repository.findProductsWithBrand(4L)).thenReturn(List.of(first, second, third));
        when(repository.findProductsWithBrandForUpdate(4L)).thenReturn(List.of(first, second, third));

        ProductBrandService.BrandNormalizationReport report = service.report(4L);
        ProductBrandService.BrandNormalizationResult result = service.normalizeExisting(4L);
        ProductBrandService.BrandNormalizationResult repeated = service.normalizeExisting(4L);

        assertThat(report.productsAffected()).isEqualTo(2);
        assertThat(report.groups()).hasSize(1);
        assertThat(report.groups().getFirst().canonicalBrand()).isEqualTo("Gamesa");
        assertThat(result.changedProducts()).isEqualTo(2);
        assertThat(repeated.changedProducts()).isZero();
        assertThat(first.getBrand()).isEqualTo("Gamesa");
        assertThat(second.getBrand()).isEqualTo("Gamesa");
        assertThat(third.getBrand()).isEqualTo("Games");
    }

    private Product product(Long id, String brand) {
        Product product = new Product();
        product.setId(id);
        product.setBrand(brand);
        return product;
    }
}
