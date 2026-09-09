package com.tienda.pos.catalog;

import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.settings.BusinessSettingsRepository;
import com.tienda.pos.tenant.PublicTenantResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@NormalMode
public class CatalogService {

    private static final int PAGE_SIZE = 24;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final PublicTenantResolver publicTenantResolver;

    public CatalogService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          BusinessSettingsRepository settingsRepository, PublicTenantResolver publicTenantResolver) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.settingsRepository = settingsRepository;
        this.publicTenantResolver = publicTenantResolver;
    }

    @Transactional(readOnly = true)
    public BusinessSettings settings() {
        Long tenantId = publicTenantResolver.resolve().getId();
        return settingsRepository.findFirstByTenantIdOrderByIdAsc(tenantId).orElseGet(BusinessSettings::new);
    }

    @Transactional(readOnly = true)
    public Page<CatalogProductView> search(String q, Long categoryId, int page) {
        Long tenantId = publicTenantResolver.resolve().getId();
        Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE);
        String query = q == null ? "" : q.trim();
        return productRepository.catalogSearch(tenantId, query, categoryId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public List<CatalogProductView> promotions() {
        Long tenantId = publicTenantResolver.resolve().getId();
        return productRepository.findCatalogPromotions(tenantId, PageRequest.of(0, 4)).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<?> activeCategories() {
        return categoryRepository.findByTenantIdAndActiveTrueOrderByNameAsc(publicTenantResolver.resolve().getId());
    }

    @Transactional(readOnly = true)
    public CatalogProductView detail(Long id) {
        return productRepository.findByIdAndTenantIdAndActiveTrue(id, publicTenantResolver.resolve().getId()).map(this::toView).orElse(null);
    }

    public String title(BusinessSettings settings) {
        if (settings.getCatalogTitle() != null && !settings.getCatalogTitle().isBlank()) {
            return settings.getCatalogTitle().trim();
        }
        return settings.getStoreName() == null || settings.getStoreName().isBlank() ? "Mi tienda" : settings.getStoreName().trim();
    }

    public String subtitle(BusinessSettings settings) {
        if (settings.getCatalogSubtitle() != null && !settings.getCatalogSubtitle().isBlank()) {
            return settings.getCatalogSubtitle().trim();
        }
        return "Consulta nuestros productos";
    }

    private CatalogProductView toView(Product product) {
        return new CatalogProductView(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getPresentation(),
                details(product),
                product.getCategory() == null ? null : product.getCategory().getName(),
                product.getSalePrice(),
                safeImageUrl(product.getImageUrl()),
                product.getDescription(),
                product.getCurrentStock() != null && product.getCurrentStock().compareTo(BigDecimal.ZERO) > 0
        );
    }

    private String details(Product product) {
        String brand = product.getBrand() == null ? "" : product.getBrand().trim();
        String presentation = product.getPresentation() == null ? "" : product.getPresentation().trim();
        return (brand + " " + presentation).trim();
    }

    private String safeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String value = imageUrl.trim();
        return value.startsWith("https://") || value.startsWith("/uploads/products/") || value.startsWith("/uploads/catalog/")
                ? value
                : null;
    }
}
