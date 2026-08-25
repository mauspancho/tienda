package com.tienda.pos.product;

import com.tienda.pos.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validJpegIsProcessedAndStoredAsLocalReference() throws Exception {
        ProductImageService service = service();
        MockMultipartFile file = imageFile("photo.jpg", "image/jpeg", 1200, 900);

        String imageUrl = service.store(file);

        assertThat(imageUrl).startsWith("/uploads/products/").endsWith(".jpg");
        Path stored = tempDir.resolve(imageUrl.substring("/uploads/products/".length())).normalize();
        assertThat(stored).exists().isRegularFile();
        BufferedImage storedImage = ImageIO.read(stored.toFile());
        assertThat(storedImage.getWidth()).isEqualTo(800);
        assertThat(storedImage.getHeight()).isEqualTo(600);
    }

    @Test
    void tooLargeUploadIsRejectedBeforeDecode() {
        ProductImageService service = service();
        MockMultipartFile file = new MockMultipartFile("imageFile", "big.jpg", "image/jpeg", new byte[(5 * 1024 * 1024) + 1]);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("superar");
    }

    @Test
    void invalidMimeIsRejected() throws Exception {
        ProductImageService service = service();
        MockMultipartFile file = new MockMultipartFile("imageFile", "page.html", "text/html", jpegBytes(100, 100));

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Formato");
    }

    @Test
    void fakeImageIsRejectedWhenItCannotBeDecoded() {
        ProductImageService service = service();
        MockMultipartFile file = new MockMultipartFile("imageFile", "fake.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("imagen válida");
    }

    @Test
    void externalHttpsUrlIsKeptWithoutDownload() {
        ProductImageService service = service();

        assertThat(service.cleanImageReference("https://images.openfoodfacts.org/product.jpg"))
                .isEqualTo("https://images.openfoodfacts.org/product.jpg");
    }

    @Test
    void localReplacementCanDeletePreviousLocalImage() throws Exception {
        ProductImageService service = service();
        String imageUrl = service.store(imageFile("old.jpg", "image/jpeg", 300, 300));
        Path stored = tempDir.resolve(imageUrl.substring("/uploads/products/".length())).normalize();
        assertThat(stored).exists();

        service.deleteLocalImage(imageUrl);

        assertThat(stored).doesNotExist();
    }

    @Test
    void pathTraversalIsRejectedWhenResolvingLocalImage() {
        ProductImageService service = service();

        assertThatThrownBy(() -> service.deleteLocalImage("/uploads/products/../../etc/passwd"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("inválida");
    }

    @Test
    void unsafeImageUrlSchemesAreRejected() {
        ProductImageService service = service();

        assertThatThrownBy(() -> service.cleanImageReference("javascript:alert(1)"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("https://");
    }

    private ProductImageService service() {
        ProductImagesProperties properties = new ProductImagesProperties();
        properties.setDirectory(tempDir.toString());
        properties.setMaxUploadSize(DataSize.ofMegabytes(5));
        properties.setMaxWidth(800);
        properties.setMaxHeight(800);
        return new ProductImageService(properties);
    }

    private MockMultipartFile imageFile(String name, String contentType, int width, int height) throws Exception {
        return new MockMultipartFile("imageFile", name, contentType, jpegBytes(width, height));
    }

    private byte[] jpegBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}