package com.tienda.pos.api.v1.product;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductBarcodeLookupResult;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Set;

import static com.tienda.pos.api.v1.product.ApiProductModels.ProductRequest;
import static com.tienda.pos.api.v1.product.ApiProductModels.ProductResponse;
import static com.tienda.pos.api.v1.product.ApiProductModels.StateRequest;

@RestController
@RequestMapping("/api/v1/products")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiProductController {

    private static final Set<String> SORTS = Set.of("name", "brand", "code", "salePrice", "purchaseCost", "currentStock", "id");

    private final ProductRepository repository;
    private final ProductService service;

    public ApiProductController(ProductRepository repository, ProductService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public ApiPageResponse<ProductResponse> list(@RequestParam(defaultValue = "") String q,
                                                 @RequestParam(defaultValue = "") String name,
                                                 @RequestParam(defaultValue = "") String brand,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) BigDecimal minPrice,
                                                 @RequestParam(required = false) BigDecimal maxPrice,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(required = false) Boolean whatsapp,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(defaultValue = "name,asc") String sort) {
        Pageable pageable = pageable(page, size, sort);
        Page<Product> products = repository.filter(normalize(q), normalize(name), normalize(brand), categoryId,
                nonNegative(minPrice), nonNegative(maxPrice), active, whatsapp, pageable);
        return ApiPageResponse.from(products, ProductResponse::from);
    }

    @GetMapping("/{id}")
    public ProductResponse detail(@PathVariable Long id) {
        return ProductResponse.from(find(id));
    }

    @GetMapping("/search")
    public ApiPageResponse<ProductResponse> search(@RequestParam String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<Product> products = repository.filter(normalize(q), "", "", null, null, null, true, null,
                PageRequest.of(Math.max(page, 0), boundedSize(size), Sort.by("name").ascending()));
        return ApiPageResponse.from(products, ProductResponse::from);
    }

    @GetMapping("/barcode/{barcode}")
    public ProductResponse barcode(@PathVariable String barcode) {
        Product product = repository.findByBarcodeAndActiveTrue(barcode)
                .orElseThrow(() -> new ApiNotFoundException("Producto no encontrado."));
        return ProductResponse.from(repository.findDetailedById(product.getId()).orElse(product));
    }

    @GetMapping("/code/{code}")
    public ProductResponse code(@PathVariable String code) {
        Product product = repository.findByCode(code)
                .orElseThrow(() -> new ApiNotFoundException("Producto no encontrado."));
        return ProductResponse.from(repository.findDetailedById(product.getId()).orElse(product));
    }

    @GetMapping("/barcode/{barcode}/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductBarcodeLookupResult lookup(@PathVariable String barcode) {
        return service.lookupByBarcode(barcode);
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        Product saved = service.save(request.toForm(null));
        return ProductResponse.from(find(saved.getId()));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse createWithImage(@Valid @RequestPart("product") ProductRequest request,
                                           @RequestPart(value = "image", required = false) MultipartFile image) {
        Product saved = service.save(request.toForm(null), image);
        return ProductResponse.from(find(saved.getId()));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        Product saved = service.save(request.toForm(id));
        return ProductResponse.from(find(saved.getId()));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateWithImage(@PathVariable Long id,
                                           @Valid @RequestPart("product") ProductRequest request,
                                           @RequestPart(value = "image", required = false) MultipartFile image) {
        Product saved = service.save(request.toForm(id), image);
        return ProductResponse.from(find(saved.getId()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse active(@PathVariable Long id, @RequestBody StateRequest request) {
        service.setActive(id, request.enabled());
        return ProductResponse.from(find(id));
    }

    @PatchMapping("/{id}/promotion")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse promotion(@PathVariable Long id, @RequestBody StateRequest request) {
        if (request.enabled()) service.promote(id); else service.removePromotion(id);
        return ProductResponse.from(find(id));
    }

    @PatchMapping("/{id}/whatsapp-promotion")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse whatsappPromotion(@PathVariable Long id, @RequestBody StateRequest request) {
        service.setWhatsappPromotion(id, request.enabled());
        return ProductResponse.from(find(id));
    }

    private Product find(Long id) {
        return repository.findDetailedById(id).orElseThrow(() -> new ApiNotFoundException("Producto no encontrado."));
    }

    private Pageable pageable(int page, int size, String sortValue) {
        String[] parts = sortValue == null ? new String[0] : sortValue.split(",", 2);
        String property = parts.length > 0 && SORTS.contains(parts[0]) ? parts[0] : "name";
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(Math.max(page, 0), boundedSize(size),
                Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private int boundedSize(int size) { return Math.max(1, Math.min(size, 100)); }
    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private BigDecimal nonNegative(BigDecimal value) { return value == null ? null : value.max(BigDecimal.ZERO); }
}
