package com.tienda.pos.api;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductBarcodeLookupResult;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/admin/api/products")
@NormalMode
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ProductApiController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    public ProductApiController(ProductRepository productRepository, ProductService productService) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping("/barcode/{barcode}/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductBarcodeLookupResult lookupBarcode(@PathVariable String barcode) {
        return productService.lookupByBarcode(barcode);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductDto> byBarcode(@PathVariable String barcode) {
        return productRepository.findByBarcodeAndActiveTrue(barcode)
                .map(ProductDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<ProductDto> search(@RequestParam String q) {
        return productRepository.quickSearch(q, PageRequest.of(0, 12)).stream().map(ProductDto::from).toList();
    }

    public record ProductDto(Long id, String code, String barcode, String name, BigDecimal price,
                             BigDecimal stock, String unit, String imageUrl) {
        static ProductDto from(Product product) {
            return new ProductDto(product.getId(), product.getCode(), product.getBarcode(), product.getName(),
                    product.getSalePrice(), product.getCurrentStock(), product.getUnit().name(), product.getImageUrl());
        }
    }
}
