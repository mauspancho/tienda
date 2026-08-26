package com.tienda.pos.catalog;

import java.math.BigDecimal;

public record CatalogProductView(
        Long id,
        String name,
        String brand,
        String presentation,
        String details,
        String category,
        BigDecimal salePrice,
        String imageUrl,
        String description,
        boolean available
) {
}
