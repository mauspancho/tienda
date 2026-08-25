package com.tienda.pos.product;

import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
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
import com.tienda.pos.supplier.SupplierRepository;
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
    private final ExternalProductService externalProductService;
    private final ProductImageService productImageService;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SupplierRepository supplierRepository, InventoryMovementRepository movementRepository,
                          ExternalProductService externalProductService, ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
        this.externalProductService = externalProductService;
        this.productImageService = productImageService;
    }

    @Transactional
    public Product save(ProductForm form) {
        return save(form, null);
    }

    @Transactional
    public Product save(ProductForm form, MultipartFile imageFile) {
        Product product = form.getId() == null ? new Product() : productRepository.findByIdForUpdate(form.getId())
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        BigDecimal previousStock = product.getCurrentStock() == null ? BigDecimal.ZERO : product.getCurrentStock();
        String previousImageUrl = product.getImageUrl();
        String newLocalImageUrl = null;
        boolean uploadedImage = imageFile != null && !imageFile.isEmpty();
        try {
            String barcode = blankToNull(form.getBarcode());
            validateUniqueCode(form.getCode(), form.getId());
            validateUniqueBarcode(barcode, form.getId());
            String imageUrl = form.isRemoveImage() ? null : productImageService.cleanImageReference(form.getImageUrl());
            if (uploadedImage) {
                newLocalImageUrl = productImageService.store(imageFile);
                imageUrl = newLocalImageUrl;
            }
            product.setCode(form.getCode().trim());
            product.setBarcode(barcode);
            product.setName(form.getName().trim());
            product.setBrand(blankToNull(form.getBrand()));
            product.setPresentation(blankToNull(form.getPresentation()));
            product.setImageUrl(imageUrl);
            product.setDescription(form.getDescription());
            product.setCategory(form.getCategoryId() == null ? null : categoryRepository.findById(form.getCategoryId()).orElse(null));
            product.setSupplier(form.getSupplierId() == null ? null : supplierRepository.findById(form.getSupplierId()).orElse(null));
            product.setPurchaseCost(MoneyUtils.money(form.getPurchaseCost()));
            product.setSalePrice(MoneyUtils.money(form.getSalePrice()));
            product.setCurrentStock(form.getCurrentStock());
            product.setMinimumStock(form.getMinimumStock());
            product.setUnit(form.getUnit());
            product.setTax(MoneyUtils.money(form.getTax()));
            product.setActive(form.isActive());
            product.setUpdatedBy(CurrentUser.username());
            Product saved = productRepository.save(product);
            if (form.getId() == null && form.getCurrentStock().compareTo(BigDecimal.ZERO) > 0
                    || form.getId() != null && previousStock.compareTo(form.getCurrentStock()) != 0) {
                InventoryMovement movement = new InventoryMovement();
                movement.setProduct(saved);
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
        String barcode = normalizeBarcode(rawBarcode);
        if (barcode == null) {
            return ProductBarcodeLookupResult.notFound(rawBarcode == null ? "" : rawBarcode.trim());
        }
        Optional<Product> local = productRepository.findByBarcode(barcode);
        if (local.isPresent()) {
            return ProductBarcodeLookupResult.localFound(local.get());
        }
        try {
            Optional<ExternalProductDto> external = externalProductService.findByBarcode(barcode);
            if (external.isEmpty()) {
                return ProductBarcodeLookupResult.notFound(barcode);
            }
            ExternalProductDto dto = external.get();
            CategoryMatch categoryMatch = findEquivalentCategory(dto.category());
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
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = "PRD-" + randomDigits(8);
            if (!productRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new DomainException("No fue posible generar un código de producto único.");
    }

    public String generateUniqueBarcode() {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = generateInternalEan13();
            if (!productRepository.existsByBarcode(candidate)) {
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

    private void validateUniqueCode(String code, Long currentId) {
        if (code == null || code.isBlank()) return;
        productRepository.findByCode(code.trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> { throw new DomainException("Ya existe un producto con ese código."); });
    }

    private void validateUniqueBarcode(String barcode, Long currentId) {
        if (barcode == null) return;
        productRepository.findByBarcode(barcode)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> { throw new DomainException("Ya existe un producto con ese código de barras."); });
    }

    private CategoryMatch findEquivalentCategory(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return new CategoryMatch(null, null);
        }
        String normalizedSuggestion = normalizeText(suggestion);
        List<Category> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
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
