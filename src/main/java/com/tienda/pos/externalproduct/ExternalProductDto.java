package com.tienda.pos.externalproduct;

public record ExternalProductDto(
        String barcode,
        String name,
        String brand,
        String presentation,
        String category,
        String imageUrl
) {
}
