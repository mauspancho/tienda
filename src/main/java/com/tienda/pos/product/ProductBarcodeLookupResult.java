package com.tienda.pos.product;

import java.math.BigDecimal;

public record ProductBarcodeLookupResult(
        ProductBarcodeLookupStatus status,
        Long productId,
        String barcode,
        String code,
        String name,
        BigDecimal stock,
        BigDecimal price,
        String brand,
        String presentation,
        String categorySuggestion,
        Long categoryId,
        String imageUrl
) {
    public static ProductBarcodeLookupResult localFound(Product product) {
        return new ProductBarcodeLookupResult(
                ProductBarcodeLookupStatus.LOCAL_FOUND,
                product.getId(),
                product.getBarcode(),
                product.getCode(),
                product.getName(),
                product.getCurrentStock(),
                product.getSalePrice(),
                product.getBrand(),
                product.getPresentation(),
                null,
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getImageUrl());
    }

    public static ProductBarcodeLookupResult externalFound(String barcode, String name, String brand,
                                                            String presentation, String categorySuggestion,
                                                            Long categoryId, String imageUrl) {
        return new ProductBarcodeLookupResult(ProductBarcodeLookupStatus.EXTERNAL_FOUND, null, barcode, null,
                name, null, null, brand, presentation, categorySuggestion, categoryId, imageUrl);
    }

    public static ProductBarcodeLookupResult notFound(String barcode) {
        return new ProductBarcodeLookupResult(ProductBarcodeLookupStatus.NOT_FOUND, null, barcode, null,
                null, null, null, null, null, null, null, null);
    }

    public static ProductBarcodeLookupResult externalError(String barcode) {
        return new ProductBarcodeLookupResult(ProductBarcodeLookupStatus.EXTERNAL_ERROR, null, barcode, null,
                null, null, null, null, null, null, null, null);
    }
}
