package com.tienda.pos.backup;

import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class StoreBackupServiceIntegrationTest {

    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "tienda-backup-test");
    private static final Path PRODUCTS = ROOT.resolve("products");
    private static final Path CATALOG = ROOT.resolve("catalog");

    static {
        System.setProperty("tienda.setup-mode", "false");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:store-backup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("tienda.product-images.directory", PRODUCTS::toString);
        registry.add("tienda.catalog-images.directory", CATALOG::toString);
        registry.add("tienda.api.jwt-secret", () -> "test-secret-with-at-least-thirty-two-bytes");
    }

    @Autowired StoreBackupService backupService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void prepareFiles() throws Exception {
        deleteRecursively(ROOT);
        Files.createDirectories(PRODUCTS);
        Files.createDirectories(CATALOG);
    }

    @Test
    void exportsAndRestoresAllTablesAndImages() throws Exception {
        Category category = new Category();
        category.setName("Bebidas respaldo");
        category = categoryRepository.saveAndFlush(category);

        Product product = new Product();
        product.setCode("BACKUP-001");
        product.setBarcode("7500000000001");
        product.setName("Producto original");
        product.setCategory(category);
        product.setPurchaseCost(new BigDecimal("12.34"));
        product.setSalePrice(new BigDecimal("19.99"));
        product.setCurrentStock(new BigDecimal("8.000"));
        product.setMinimumStock(BigDecimal.ONE);
        product.setUnit(UnitType.PIEZA);
        product.setImageUrl("/uploads/products/original.jpg");
        productRepository.saveAndFlush(product);

        Files.write(PRODUCTS.resolve("original.jpg"), new byte[]{1, 2, 3, 4});
        Files.write(CATALOG.resolve("logo.jpg"), new byte[]{5, 6, 7});

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        backupService.exportArchive(output);
        byte[] archive = output.toByteArray();

        Set<String> entries = zipEntries(archive);
        assertThat(entries).contains("manifest.json", "data/product.json",
                "images/products/original.jpg", "images/catalog/logo.jpg");

        product.setName("Producto alterado");
        productRepository.saveAndFlush(product);
        Files.write(PRODUCTS.resolve("original.jpg"), new byte[]{9, 9});
        Files.write(PRODUCTS.resolve("extra.jpg"), new byte[]{8});
        Files.delete(CATALOG.resolve("logo.jpg"));

        backupService.importArchive(new MockMultipartFile("backupFile", "backup.zip", "application/zip", archive));

        Product restored = productRepository.findByCode("BACKUP-001").orElseThrow();
        assertThat(restored.getName()).isEqualTo("Producto original");
        assertThat(restored.getPurchaseCost()).isEqualByComparingTo("12.34");
        assertThat(Files.readAllBytes(PRODUCTS.resolve("original.jpg"))).containsExactly(1, 2, 3, 4);
        assertThat(PRODUCTS.resolve("extra.jpg")).doesNotExist();
        assertThat(Files.readAllBytes(CATALOG.resolve("logo.jpg"))).containsExactly(5, 6, 7);
    }

    @Test
    void backupPageIsRenderedOnlyForAdministrators() throws Exception {
        mockMvc.perform(get("/admin/backup").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/backup").with(user("cajero").roles("CAJERO")))
                .andExpect(status().isForbidden());
    }

    private Set<String> zipEntries(byte[] archive) throws Exception {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
