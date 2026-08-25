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
}
