package com.tienda.pos.product;

import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.externalproduct.ExternalProductDto;
import com.tienda.pos.externalproduct.ExternalProductLookupException;
import com.tienda.pos.externalproduct.ExternalProductService;
import com.tienda.pos.inventory.InventoryMovement;
import com.tienda.pos.inventory.InventoryMovementRepository;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryStock;
import com.tienda.pos.inventory.InventoryStockRepository;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.warehouse.Warehouse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@NormalMode
public class ProductService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_GENERATION_ATTEMPTS = 50;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryStockRepository stockRepository;
    private final StoreContextService storeContextService;
    private final ExternalProductService externalProductService;
    private final ProductImageService productImageService;
    private final CurrentTenant currentTenant;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SupplierRepository supplierRepository, InventoryMovementRepository movementRepository,
                          InventoryStockRepository stockRepository, StoreContextService storeContextService,
                          ExternalProductService externalProductService, ProductImageService productImageService,
                          CurrentTenant currentTenant) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
        this.stockRepository = stockRepository;
        this.storeContextService = storeContextService;
        this.externalProductService = externalProductService;
        this.productImageService = productImageService;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public Product save(ProductForm form) {
        return save(form, null);
    }

    @Transactional
    public Product save(ProductForm form, MultipartFile imageFile) {
        Tenant tenant = currentTenant.get();
        Long tenantId = tenant.getId();
        Product product = form.getId() == null ? new Product() : productRepository.findByIdAndTenantIdForUpdate(form.getId(), tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        BigDecimal previousStock = product.getCurrentStock() == null ? BigDecimal.ZERO : product.getCurrentStock();
        String previousImageUrl = product.getImageUrl();
        String newLocalImageUrl = null;
        boolean uploadedImage = imageFile != null && !imageFile.isEmpty();
        try {
            String barcode = blankToNull(form.getBarcode());
            validateUniqueCode(tenantId, form.getCode(), form.getId());
            validateUniqueBarcode(tenantId, barcode, form.getId());
            String imageUrl = form.isRemoveImage() ? null : productImageService.cleanImageReference(form.getImageUrl());
            if (uploadedImage) {
                newLocalImageUrl = productImageService.store(imageFile);
                imageUrl = newLocalImageUrl;
            }
            product.setTenant(tenant);
            product.setCode(form.getCode().trim());
            product.setBarcode(barcode);
            product.setName(form.getName().trim());
            product.setBrand(blankToNull(form.getBrand()));
            product.setPresentation(blankToNull(form.getPresentation()));
            product.setImageUrl(imageUrl);
            product.setDescription(form.getDescription());
            product.setCategory(form.getCategoryId() == null ? null : categoryRepository.findByIdAndTenantId(form.getCategoryId(), tenantId)
                    .orElseThrow(() -> new DomainException("Categoria no encontrada.")));
            product.setSupplier(form.getSupplierId() == null ? null : supplierRepository.findByIdAndTenantId(form.getSupplierId(), tenantId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado.")));
            product.setPurchaseCost(MoneyUtils.money(form.getPurchaseCost()));
            product.setSalePrice(MoneyUtils.money(form.getSalePrice()));
            product.setCurrentStock(form.getCurrentStock());
            product.setMinimumStock(form.getMinimumStock());
            product.setUnit(form.getUnit());
            product.setTax(MoneyUtils.money(form.getTax()));
            product.setActive(form.isActive());
            product.setUpdatedBy(CurrentUser.username());
            Product saved = productRepository.save(product);
            Warehouse warehouse = storeContextService.defaultWarehouse();
            syncDefaultStock(tenant, saved, warehouse, form.getCurrentStock(), form.getMinimumStock());
            if (form.getId() == null && form.getCurrentStock().compareTo(BigDecimal.ZERO) > 0
                    || form.getId() != null && previousStock.compareTo(form.getCurrentStock()) != 0) {
                InventoryMovement movement = new InventoryMovement();
                movement.setTenant(tenant);
                movement.setProduct(saved);
                movement.setBranch(warehouse.getBranch());
                movement.setWarehouse(warehouse);
                movement.setMovementType(InventoryMovementType.INITIAL_STOCK);
                movement.setPreviousStock(previousStock);
                movement.setQuantity(form.getCurrentStock().subtract(previousStock));
                movement.setNewStock(form.getCurrentStock());
                movement.setReferenceType("PRODUCT");
                movement.setReferenceId(saved.getId());
                movement.setNotes("Inventario inicial/actualización desde ficha de producto");
                movement.setCreatedBy(CurrentUser.username());
                movementRepository.save(movement);
            }
            scheduleImageCleanup(previousImageUrl, newLocalImageUrl, product.getImageUrl());
            return saved;
        } catch (RuntimeException ex) {
            if (newLocalImageUrl != null) {
                productImageService.deleteLocalImage(newLocalImageUrl);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ProductBarcodeLookupResult lookupByBarcode(String rawBarcode) {
        Long tenantId = currentTenant.id();
        String barcode = normalizeBarcode(rawBarcode);
        if (barcode == null) {
            return ProductBarcodeLookupResult.notFound(rawBarcode == null ? "" : rawBarcode.trim());
        }
        Optional<Product> local = productRepository.findByTenantIdAndBarcode(tenantId, barcode);
        if (local.isPresent()) {
            return ProductBarcodeLookupResult.localFound(local.get());
        }
        try {
            Optional<ExternalProductDto> external = externalProductService.findByBarcode(barcode);
            if (external.isEmpty()) {
                return ProductBarcodeLookupResult.notFound(barcode);
            }
            ExternalProductDto dto = external.get();
            CategoryMatch categoryMatch = findEquivalentCategory(tenantId, dto.category());
            return ProductBarcodeLookupResult.externalFound(
                    barcode,
                    dto.name(),
                    dto.brand(),
                    dto.presentation(),
                    categoryMatch.suggestion(),
                    categoryMatch.categoryId(),
                    dto.imageUrl());
        } catch (ExternalProductLookupException ex) {
            return ProductBarcodeLookupResult.externalError(barcode);
        }
    }

    public String generateUniqueProductCode() {
        Long tenantId = currentTenant.id();
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = "PRD-" + randomDigits(8);
            if (!productRepository.existsByTenantIdAndCode(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new DomainException("No fue posible generar un código de producto único.");
    }

    public String generateUniqueBarcode() {
        Long tenantId = currentTenant.id();
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = generateInternalEan13();
            if (!productRepository.existsByTenantIdAndBarcode(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new DomainException("No fue posible generar un código de barras único.");
    }

    public static String normalizeBarcode(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", "").trim();
        if (normalized.isBlank() || !normalized.matches("\\d+")) return null;
        return normalized;
    }

    private void syncDefaultStock(Tenant tenant, Product product, Warehouse warehouse, BigDecimal currentStock, BigDecimal minimumStock) {
        InventoryStock stock = stockRepository.findByProductAndWarehouseAndTenantId(product, warehouse, tenant.getId())
                .orElseGet(() -> {
                    InventoryStock created = new InventoryStock();
                    created.setTenant(tenant);
                    created.setProduct(product);
                    created.setWarehouse(warehouse);
                    return created;
                });
        stock.setTenant(tenant);
        stock.setQuantity(currentStock == null ? BigDecimal.ZERO : currentStock);
        stock.setMinimumStock(minimumStock == null ? BigDecimal.ZERO : minimumStock);
        stockRepository.save(stock);
    }

    private void scheduleImageCleanup(String previousImageUrl, String newLocalImageUrl, String finalImageUrl) {
        boolean replacedPreviousLocal = productImageService.isLocalImage(previousImageUrl)
                && !Objects.equals(previousImageUrl, finalImageUrl);
        if (!replacedPreviousLocal && newLocalImageUrl == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (replacedPreviousLocal) {
                productImageService.deleteLocalImage(previousImageUrl);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (replacedPreviousLocal) {
                    productImageService.deleteLocalImage(previousImageUrl);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED && newLocalImageUrl != null) {
                    productImageService.deleteLocalImage(newLocalImageUrl);
                }
            }
        });
    }

    @Transactional
    public void promote(Long productId) {
        Long tenantId = currentTenant.id();
        Product product = productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        if (product.isPromoted()) {
            return;
        }
        if (productRepository.countByTenantIdAndPromotedTrue(tenantId) >= 4) {
            throw new DomainException("Ya tienes 4 productos promocionados. Quita uno antes de agregar otro.");
        }
        product.setPromoted(true);
        product.setPromotionOrder(productRepository.maxPromotionOrder(tenantId) + 1);
        productRepository.save(product);
    }

    @Transactional
    public void removePromotion(Long productId) {
        Long tenantId = currentTenant.id();
        Product product = productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        product.setPromoted(false);
        product.setPromotionOrder(null);
        productRepository.save(product);
    }

    private void validateUniqueCode(Long tenantId, String code, Long currentId) {
        if (code == null || code.isBlank()) return;
        productRepository.findByTenantIdAndCode(tenantId, code.trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> { throw new DomainException("Ya existe un producto con ese código."); });
    }

    private void validateUniqueBarcode(Long tenantId, String barcode, Long currentId) {
        if (barcode == null) return;
        productRepository.findByTenantIdAndBarcode(tenantId, barcode)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> { throw new DomainException("Ya existe un producto con ese código de barras."); });
    }

    private CategoryMatch findEquivalentCategory(Long tenantId, String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return new CategoryMatch(null, null);
        }
        String normalizedSuggestion = normalizeText(suggestion);
        List<Category> categories = categoryRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        for (Category category : categories) {
            if (normalizeText(category.getName()).equals(normalizedSuggestion)) {
                return new CategoryMatch(suggestion, category.getId());
            }
        }
        return new CategoryMatch(suggestion, null);
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
        return normalized.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateInternalEan13() {
        String base = "20" + randomDigits(10);
        return base + ean13CheckDigit(base);
    }

    private String randomDigits(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }

    static int ean13CheckDigit(String base) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            int digit = Character.digit(base.charAt(i), 10);
            sum += digit * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10;
    }

    private record CategoryMatch(String suggestion, Long categoryId) {
    }
}
