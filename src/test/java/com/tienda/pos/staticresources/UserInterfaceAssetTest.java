package com.tienda.pos.staticresources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserInterfaceAssetTest {

    private static final Path ROOT = Path.of("");

    @Test
    void userRoleCheckboxesHaveVisibleStateIndicators() throws Exception {
        String users = Files.readString(ROOT.resolve("src/main/resources/templates/users/index.html"));
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        assertThat(users)
                .contains("user-access-box")
                .contains("user-access-label")
                .contains("th:field=\"*{admin}\"")
                .contains("th:field=\"*{cashier}\"")
                .contains("th:field=\"*{active}\"");
        assertThat(css)
                .contains(".user-access-input:checked + .user-access-box")
                .contains("content: \"✓\"")
                .contains("content: \"×\"")
                .contains(".user-access-option:has(.user-access-input:checked)");
    }

    @Test
    void productImagesAreConfiguredAndRenderedInProductAndPosAssets() throws Exception {
        String application = Files.readString(ROOT.resolve("src/main/resources/application.yml"));
        String productForm = Files.readString(ROOT.resolve("src/main/resources/templates/products/form.html"));
        String productIndex = Files.readString(ROOT.resolve("src/main/resources/templates/products/index.html"));
        String posScanner = Files.readString(ROOT.resolve("src/main/resources/static/js/barcode-scanner.js"));
        String productApi = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/api/ProductApiController.java"));

        assertThat(application)
                .contains("product-images:")
                .contains("directory: ./data/products")
                .contains("public-path: /uploads/products")
                .contains("max-upload-size: 5MB");
        assertThat(productForm)
                .contains("enctype=\"multipart/form-data\"")
                .contains("data-product-image-file")
                .contains("accept=\"image/jpeg,image/png,image/webp\"")
                .contains("data-product-remove-image")
                .contains("data-product-image-clear");
        assertThat(productIndex)
                .contains("product-thumb")
                .contains("th:src=\"${p.imageUrl}\"")
                .contains("product-thumb-placeholder");
        assertThat(posScanner)
                .contains("function safeImageUrl")
                .contains("function productImageNode")
                .contains("startsWith(\"/uploads/products/\")")
                .contains("productImageNode(item.imageUrl)")
                .contains("productImageNode(product.imageUrl)");
        assertThat(productApi)
                .contains("String imageUrl")
                .contains("product.getImageUrl()");
    }

    @Test
    void uploadedProductAndCatalogImagesArePublicWithoutLogin() throws Exception {
        String securityConfig = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/security/SecurityConfig.java"));

        assertThat(securityConfig)
                .contains("/uploads/products/**")
                .contains("/uploads/catalog/**")
                .contains("/admin/login")
                .contains(".permitAll()");
    }

    @Test
    void publicCatalogAssetsAndSettingsAreWired() throws Exception {
        String catalogIndex = Files.readString(ROOT.resolve("src/main/resources/templates/catalog/index.html"));
        String catalogProduct = Files.readString(ROOT.resolve("src/main/resources/templates/catalog/product.html"));
        String settings = Files.readString(ROOT.resolve("src/main/resources/templates/settings/index.html"));
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        assertThat(catalogIndex)
                .contains("catalog-slider")
                .contains("/uploads/catalog/")
                .contains("@{/producto/{id}(id=${p.id})}")
                .contains("catalogEnabled");
        assertThat(catalogProduct)
                .contains("catalog-detail")
                .contains("product.imageUrl")
                .contains("product-placeholder.svg");
        assertThat(settings)
                .contains("enctype=\"multipart/form-data\"")
                .contains("th:field=\"*{catalogEnabled}\"")
                .contains("th:field=\"*{catalogTitle}\"")
                .contains("name=\"logoFile\"")
                .contains("name=\"removeLogo\"");
        assertThat(css)
                .contains(".catalog-page")
                .contains(".logo-preview")
                .contains(".check-card:has(input:checked)");
    }
    @Test
    void dashboardProfitFiltersAreRendered() throws Exception {
        String dashboard = Files.readString(ROOT.resolve("src/main/resources/templates/dashboard/index.html"));
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        assertThat(dashboard)
                .contains("id=\"profitDate\"")
                .contains("id=\"profitPeriod\"")
                .contains("/admin/api/dashboard/profit")
                .contains("selectedDateProfit")
                .contains("selectedDateCostAdjustment")
                .contains("periodProfit")
                .contains("periodCostAdjustment");
        assertThat(css)
                .contains(".dashboard-profit-controls")
                .contains(".dashboard-profit-metrics")
                .contains(".grid-4");
    }

    @Test
    void inventoryAdjustmentsCaptureCostChanges() throws Exception {
        String inventory = Files.readString(ROOT.resolve("src/main/resources/templates/inventory/index.html"));

        assertThat(inventory)
                .contains("th:field=\"*{unitCost}\"")
                .contains("Costo unitario")
                .contains("Costo aplicado")
                .contains("Costo nuevo")
                .contains("Ajuste costo")
                .contains("m.costAdjustment");
    }
}

