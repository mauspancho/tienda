package com.tienda.pos.product;

import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String OUTPUT_FORMAT = "jpg";

    private final ProductImagesProperties properties;
    private final Path baseDirectory;
    private final String publicPath;

    public ProductImageService(ProductImagesProperties properties) {
        this.properties = properties;
        this.baseDirectory = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        this.publicPath = properties.normalizedPublicPath();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validateUploadMetadata(file);
        BufferedImage original = decode(file);
        validateOriginalDimensions(original);
        BufferedImage optimized = resize(original);
        String filename = UUID.randomUUID() + "." + OUTPUT_FORMAT;
        Path target = baseDirectory.resolve(filename).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new DomainException("Nombre de imagen inválido.");
        }
        try {
            Files.createDirectories(baseDirectory);
            writeJpeg(optimized, target);
            return publicPath + "/" + filename;
        } catch (IOException ex) {
            throw new DomainException("No fue posible guardar la imagen del producto.");
        }
    }

    public String cleanImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String value = imageUrl.trim();
        if (value.startsWith(publicPath + "/")) {
            resolveLocalImage(value);
            return value;
        }
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // handled below
        }
        throw new DomainException("La URL de imagen debe iniciar con https:// o " + publicPath + "/.");
    }

    public void deleteLocalImage(String imageUrl) {
        if (!isLocalImage(imageUrl)) {
            return;
        }
        Path path = resolveLocalImage(imageUrl);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new DomainException("No fue posible eliminar la imagen anterior del producto.");
        }
    }

    public boolean isLocalImage(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(publicPath + "/");
    }

    private void validateUploadMetadata(MultipartFile file) {
        if (file.getSize() > properties.getMaxUploadSize().toBytes()) {
            throw new DomainException("La imagen no debe superar " + properties.getMaxUploadSize().toMegabytes() + " MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new DomainException("Formato de imagen no permitido. Usa JPEG, PNG o WebP.");
        }
    }

    private BufferedImage decode(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new DomainException("El archivo seleccionado no es una imagen válida.");
            }
            return image;
        } catch (IOException ex) {
            throw new DomainException("No fue posible leer la imagen del producto.");
        }
    }

    private void validateOriginalDimensions(BufferedImage image) {
        if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > properties.getMaxOriginalWidth()
                || image.getHeight() > properties.getMaxOriginalHeight()) {
            throw new DomainException("La imagen tiene dimensiones no permitidas.");
        }
    }

    private BufferedImage resize(BufferedImage original) {
        int canvasWidth = Math.max(1, properties.getMaxWidth());
        int canvasHeight = Math.max(1, properties.getMaxHeight());
        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min((double) canvasWidth / width, (double) canvasHeight / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int x = (canvasWidth - targetWidth) / 2;
        int y = (canvasHeight - targetHeight) / 2;
        BufferedImage output = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.drawImage(original, x, y, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private void writeJpeg(BufferedImage image, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(OUTPUT_FORMAT);
        if (!writers.hasNext()) {
            ImageIO.write(image, OUTPUT_FORMAT, target.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(properties.getWebpQuality(), 1.0f)));
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private Path resolveLocalImage(String imageUrl) {
        if (!isLocalImage(imageUrl)) {
            throw new DomainException("La imagen local no pertenece al directorio permitido.");
        }
        String relative = imageUrl.substring((publicPath + "/").length());
        if (relative.isBlank()) {
            throw new DomainException("La ruta de imagen local es inválida.");
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new DomainException("La ruta de imagen local es inválida.");
        }
        return resolved;
    }
}

