package com.tienda.pos.api;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductBarcodeLookupResult;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> suggestions(@RequestParam String field,
                                    @RequestParam(defaultValue = "") String q) {
        String query = q == null ? "" : q.trim();
        return switch (field) {
            case "name" -> productRepository.suggestNames(query, PageRequest.of(0, 10));
            case "brand" -> productRepository.suggestBrands(query, PageRequest.of(0, 10));
            default -> List.of();
        };
    }

    @GetMapping("/table")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductTableResponse table(@RequestParam(defaultValue = "1") int draw,
                                      @RequestParam(defaultValue = "0") int start,
                                      @RequestParam(defaultValue = "20") int length,
                                      @RequestParam(name = "order[0][column]", defaultValue = "1") int orderColumn,
                                      @RequestParam(name = "order[0][dir]", defaultValue = "asc") String orderDirection,
                                      @RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "") String name,
                                      @RequestParam(defaultValue = "") String brand,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) BigDecimal minPrice,
                                      @RequestParam(required = false) BigDecimal maxPrice,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(required = false) Boolean whatsapp,
                                      @RequestParam MultiValueMap<String, String> requestParameters) {
        int pageSize = Math.max(10, Math.min(length, 100));
        int page = Math.max(start, 0) / pageSize;
        BigDecimal normalizedMin = nonNegative(minPrice);
        BigDecimal normalizedMax = nonNegative(maxPrice);
        if (normalizedMin != null && normalizedMax != null && normalizedMin.compareTo(normalizedMax) > 0) {
            BigDecimal previousMin = normalizedMin;
            normalizedMin = normalizedMax;
            normalizedMax = previousMin;
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(orderDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        String sortProperty = switch (orderColumn) {
            case 0 -> "code";
            case 2 -> "brand";
            case 3 -> "salePrice";
            case 4 -> "purchaseCost";
            case 5 -> "currentStock";
            default -> "name";
        };
        Sort sort = Sort.by(direction, sortProperty).and(Sort.by(Sort.Direction.ASC, "id"));
        List<String> selectedBrands = requestParameters.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("columns[2][columnControl][list]"))
                .flatMap(entry -> entry.getValue().stream())
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();
        List<String> brandList = selectedBrands.isEmpty() ? List.of("") : selectedBrands;
        Page<Product> products = productRepository.filterForTable(normalize(q), normalize(name), normalize(brand),
                selectedBrands.isEmpty(), brandList, categoryId, normalizedMin, normalizedMax, active, whatsapp,
                PageRequest.of(page, pageSize, sort));
        List<ProductTableRow> rows = products.getContent().stream().map(ProductTableRow::from).toList();
        List<ColumnControlOption> brandOptions = productRepository.suggestBrands("", PageRequest.of(0, 500)).stream()
                .map(value -> new ColumnControlOption(value, value))
                .toList();
        return new ProductTableResponse(draw, productRepository.count(), products.getTotalElements(), rows,
                Map.of("brand", brandOptions));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? null : value.max(BigDecimal.ZERO);
    }

    public record ProductTableResponse(int draw, long recordsTotal, long recordsFiltered,
                                       List<ProductTableRow> data,
                                       Map<String, List<ColumnControlOption>> columnControl) {
    }

    public record ColumnControlOption(String label, String value) {
    }

    public record ProductTableRow(Long id, String code, String barcode, String name, String brand,
                                  String category, String imageUrl, BigDecimal salePrice,
                                  BigDecimal purchaseCost, BigDecimal stock, BigDecimal margin,
                                  boolean lowStock, boolean whatsapp, boolean promoted) {
        static ProductTableRow from(Product product) {
            String category = product.getCategory() == null ? "" : product.getCategory().getName();
            return new ProductTableRow(product.getId(), product.getCode(), product.getBarcode(), product.getName(),
                    product.getBrand(), category, product.getImageUrl(), product.getSalePrice(),
                    product.getPurchaseCost(), product.getCurrentStock(), product.marginPercent(),
                    product.hasLowStock(), product.isPromocionWhatsapp(), product.isPromoted());
        }
    }

    public record ProductDto(Long id, String code, String barcode, String name, BigDecimal price,
                             BigDecimal stock, String unit, String imageUrl) {
        static ProductDto from(Product product) {
            return new ProductDto(product.getId(), product.getCode(), product.getBarcode(), product.getName(),
                    product.getSalePrice(), product.getCurrentStock(), product.getUnit().name(), product.getImageUrl());
        }
    }
}
