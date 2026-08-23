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
                .contains("COOLDOWN_MS = 1500")
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
}