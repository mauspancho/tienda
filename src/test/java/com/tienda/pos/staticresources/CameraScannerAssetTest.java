package com.tienda.pos.staticresources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CameraScannerAssetTest {

    private static final Path ROOT = Path.of("");

    @Test
    void cameraScannerAssetAndVendorBundleExist() throws Exception {
        Path scanner = ROOT.resolve("src/main/resources/static/js/camera-barcode-scanner.js");
        Path zxing = ROOT.resolve("src/main/resources/static/vendor/zxing/zxing-browser.min.js");

        assertThat(scanner).exists().isRegularFile();
        assertThat(zxing).exists().isRegularFile();
        assertThat(Files.readString(scanner))
                .contains("class CameraBarcodeScanner")
                .contains("SAME_BARCODE_RELEASE_MS = 1200")
                .contains("SUCCESS_PAUSE_MS = 650")
                .contains("lockedBarcodes")
                .contains("playScanSuccessBeep")
                .contains("camera-scan-success")
                .contains("barcode:detected")
                .contains("facingMode: { ideal: \"environment\" }");
    }

    @Test
    void productAndPosTemplatesLoadCameraScanner() throws Exception {
        String productForm = Files.readString(ROOT.resolve("src/main/resources/templates/products/form.html"));
        String pos = Files.readString(ROOT.resolve("src/main/resources/templates/pos/index.html"));

        assertThat(productForm)
                .contains("Escanear con cámara")
                .contains("/vendor/zxing/zxing-browser.min.js")
                .contains("/js/camera-barcode-scanner.js");

        assertThat(pos)
                .contains("Usar cámara")
                .contains("data-camera-mode=\"continuous\"")
                .contains("th:disabled=\"${!cashOpen}\"")
                .contains("/vendor/zxing/zxing-browser.min.js")
                .contains("/js/camera-barcode-scanner.js");
    }

    @Test
    void posCartQuantitiesUseWholeNumbers() throws Exception {
        String posScanner = Files.readString(ROOT.resolve("src/main/resources/static/js/barcode-scanner.js"));

        assertThat(posScanner)
                .contains("function normalizeCartQuantity")
                .contains("Math.floor")
                .contains("qtyInput.setAttribute(\"min\", \"1\")")
                .contains("qtyInput.setAttribute(\"step\", \"1\")")
                .contains("quantity: normalizeCartQuantity(item.quantity)");
    }

    @Test
    void posCheckoutChargesTheDisplayedTotalWithoutReceivedInput() throws Exception {
        String pos = Files.readString(ROOT.resolve("src/main/resources/templates/pos/index.html"));
        String posScanner = Files.readString(ROOT.resolve("src/main/resources/static/js/barcode-scanner.js"));

        assertThat(pos)
                .doesNotContain("data-received")
                .doesNotContain("Recibido");
        assertThat(posScanner)
                .contains("function cartTotal()")
                .contains("receivedAmount: cartTotal()")
                .doesNotContain("receivedInput")
                .doesNotContain("[data-received]");
    }
}
