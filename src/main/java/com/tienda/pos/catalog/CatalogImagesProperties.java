package com.tienda.pos.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "tienda.catalog-images")
public class CatalogImagesProperties {

    private String directory = "./data/catalog";
    private String publicPath = "/uploads/catalog";
    private DataSize maxUploadSize = DataSize.ofMegabytes(5);
    private int maxWidth = 1200;
    private int maxHeight = 600;
    private float imageQuality = 0.82f;
    private int maxOriginalWidth = 12000;
    private int maxOriginalHeight = 12000;

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public String getPublicPath() { return publicPath; }
    public void setPublicPath(String publicPath) { this.publicPath = publicPath; }
    public DataSize getMaxUploadSize() { return maxUploadSize; }
    public void setMaxUploadSize(DataSize maxUploadSize) { this.maxUploadSize = maxUploadSize; }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }
    public float getImageQuality() { return imageQuality; }
    public void setImageQuality(float imageQuality) { this.imageQuality = imageQuality; }
    public int getMaxOriginalWidth() { return maxOriginalWidth; }
    public void setMaxOriginalWidth(int maxOriginalWidth) { this.maxOriginalWidth = maxOriginalWidth; }
    public int getMaxOriginalHeight() { return maxOriginalHeight; }
    public void setMaxOriginalHeight(int maxOriginalHeight) { this.maxOriginalHeight = maxOriginalHeight; }

    public String normalizedPublicPath() {
        if (publicPath == null || publicPath.isBlank()) {
            return "/uploads/catalog";
        }
        String normalized = publicPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
