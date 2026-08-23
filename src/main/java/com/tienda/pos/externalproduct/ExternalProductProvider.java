package com.tienda.pos.externalproduct;

import java.util.Optional;

public interface ExternalProductProvider {

    String name();

    Optional<ExternalProductDto> findByBarcode(String barcode);
}
