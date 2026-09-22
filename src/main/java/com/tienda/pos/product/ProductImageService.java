package com.tienda.pos.product;

import com.tienda.pos.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String OUTPUT_FORMAT = "jpg";
    private static final Duration REMOTE_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REMOTE_READ_TIMEOUT = Duration.ofSeconds(8);

    private final ProductImagesProperties properties;
    private final Path baseDirectory;
    private final String publicPath;
    private final RemoteImageFetcher remoteImageFetcher;

    @Autowired
    public ProductImageService(ProductImagesProperties properties) {
        this(properties, new HttpRemoteImageFetcher());
    }

    ProductImageService(ProductImagesProperties properties, RemoteImageFetcher remoteImageFetcher) {
        this.properties = properties;
        this.baseDirectory = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        this.publicPath = properties.normalizedPublicPath();
        this.remoteImageFetcher = remoteImageFetcher;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validateUploadMetadata(file);
        BufferedImage original = decode(file);
        return storeDecodedImage(original);
    }

    public String storeOpenFoodFactsImage(String imageUrl) {
        URI uri = parseOpenFoodFactsUri(imageUrl);
        try {
            DownloadedImage downloaded = remoteImageFetcher.fetch(uri, properties.getMaxUploadSize().toBytes());
            validateDownloadedImage(downloaded);
            return storeDecodedImage(decode(downloaded.bytes()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Open Food Facts image download interrupted url={}", uri);
            return null;
        } catch (IOException ex) {
            log.warn("Open Food Facts image download failed url={} message={}", uri, ex.getMessage());
            return null;
        } catch (DomainException ex) {
            log.warn("Open Food Facts image was not stored url={} message={}", uri, ex.getMessage());
            return null;
        }
    }

    public boolean isOpenFoodFactsImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        try {
            return isOpenFoodFactsUri(URI.create(imageUrl.trim()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String storeDecodedImage(BufferedImage original) {
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

    private URI parseOpenFoodFactsUri(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl == null ? "" : imageUrl.trim());
            if (!isOpenFoodFactsUri(uri)) {
                throw new DomainException("La imagen no pertenece a Open Food Facts.");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new DomainException("La URL de imagen de Open Food Facts es inválida.");
        }
    }

    private static boolean isOpenFoodFactsUri(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && (host.equalsIgnoreCase("openfoodfacts.org")
                || host.toLowerCase(Locale.ROOT).endsWith(".openfoodfacts.org"));
    }

    private void validateDownloadedImage(DownloadedImage downloaded) {
        if (downloaded.bytes().length == 0 || downloaded.bytes().length > properties.getMaxUploadSize().toBytes()) {
            throw new DomainException("La imagen de Open Food Facts supera el tamaño permitido.");
        }
        String contentType = downloaded.contentType() == null
                ? ""
                : downloaded.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new DomainException("Open Food Facts devolvió un archivo que no es una imagen permitida.");
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
            return decode(inputStream);
        } catch (IOException ex) {
            throw new DomainException("No fue posible leer la imagen del producto.");
        }
    }

    private BufferedImage decode(byte[] bytes) {
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return decode(inputStream);
        } catch (IOException ex) {
            throw new DomainException("No fue posible leer la imagen del producto.");
        }
    }

    private BufferedImage decode(InputStream inputStream) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new DomainException("El archivo seleccionado no es una imagen válida.");
        }
        return image;
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

    @FunctionalInterface
    interface RemoteImageFetcher {
        DownloadedImage fetch(URI uri, long maxBytes) throws IOException, InterruptedException;
    }

    record DownloadedImage(byte[] bytes, String contentType) {
    }

    private static final class HttpRemoteImageFetcher implements RemoteImageFetcher {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REMOTE_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        @Override
        public DownloadedImage fetch(URI uri, long maxBytes) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REMOTE_READ_TIMEOUT)
                    .header("User-Agent", "TiendaPOS/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300
                        || !isOpenFoodFactsUri(response.uri())) {
                    throw new IOException("Open Food Facts image request failed with status " + response.statusCode());
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (contentLength > maxBytes) {
                    throw new IOException("Open Food Facts image exceeds the configured size limit");
                }
                int readLimit = (int) Math.min(maxBytes + 1, Integer.MAX_VALUE);
                byte[] bytes = body.readNBytes(readLimit);
                if (bytes.length > maxBytes) {
                    throw new IOException("Open Food Facts image exceeds the configured size limit");
                }
                String contentType = response.headers().firstValue("Content-Type")
                        .map(value -> value.split(";", 2)[0].trim())
                        .orElse("");
                return new DownloadedImage(bytes, contentType);
            }
        }
    }
}

