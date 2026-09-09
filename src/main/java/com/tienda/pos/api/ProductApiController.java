package com.tienda.pos.api;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductBarcodeLookupResult;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.ProductService;
import com.tienda.pos.tenant.CurrentTenant;
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
    private final CurrentTenant currentTenant;

    public ProductApiController(ProductRepository productRepository, ProductService productService, CurrentTenant currentTenant) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/barcode/{barcode}/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductBarcodeLookupResult lookupBarcode(@PathVariable String barcode) {
        return productService.lookupByBarcode(barcode);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductDto> byBarcode(@PathVariable String barcode) {
        return productRepository.findByTenantIdAndBarcodeAndActiveTrue(currentTenant.id(), barcode)
                .map(ProductDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<ProductDto> search(@RequestParam String q) {
        return productRepository.quickSearch(currentTenant.id(), q, PageRequest.of(0, 12)).stream().map(ProductDto::from).toList();
    }

    public record ProductDto(Long id, String code, String barcode, String name, BigDecimal price,
                             BigDecimal stock, String unit, String imageUrl) {
        static ProductDto from(Product product) {
            return new ProductDto(product.getId(), product.getCode(), product.getBarcode(), product.getName(),
                    product.getSalePrice(), product.getCurrentStock(), product.getUnit().name(), product.getImageUrl());
        }
    }
}