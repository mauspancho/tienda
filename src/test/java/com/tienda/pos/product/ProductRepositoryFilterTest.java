package com.tienda.pos.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProductRepositoryFilterTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:product-filter;MODE=MySQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void combinesBrandCategoryPriceStatusAndWhatsappFiltersWithSorting() {
        Category drinks = new Category();
        drinks.setName("Bebidas");
        drinks = categoryRepository.save(drinks);

        productRepository.save(product("A-1", "Agua chica", "Acme", "15.00", drinks, true, true));
        productRepository.save(product("A-2", "Agua grande", "Acme", "25.00", drinks, true, true));
        productRepository.save(product("B-1", "Agua sabor", "Otra", "20.00", drinks, true, true));
        productRepository.save(product("A-3", "Agua inactiva", "Acme", "22.00", drinks, false, true));

        Page<Product> result = productRepository.filter("", "agua", "acme", drinks.getId(),
                new BigDecimal("10.00"), new BigDecimal("30.00"), true, true,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "salePrice")));

        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactly("Agua grande", "Agua chica");

        assertThat(productRepository.suggestNames("gran", PageRequest.of(0, 10)))
                .containsExactly("Agua grande");
        assertThat(productRepository.suggestBrands("ac", PageRequest.of(0, 10)))
                .containsExactly("Acme");
    }

    private Product product(String code, String name, String brand, String salePrice,
                            Category category, boolean active, boolean whatsapp) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setBrand(brand);
        product.setCategory(category);
        product.setPurchaseCost(new BigDecimal("8.00"));
        product.setSalePrice(new BigDecimal(salePrice));
        product.setCurrentStock(BigDecimal.TEN);
        product.setMinimumStock(BigDecimal.ONE);
        product.setActive(active);
        product.setPromocionWhatsapp(whatsapp);
        return product;
    }
}
