package com.tienda.pos.product;

import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovement;
import com.tienda.pos.inventory.InventoryMovementRepository;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.supplier.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@NormalMode
public class ProductService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_GENERATION_ATTEMPTS = 50;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryMovementRepository movementRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SupplierRepository supplierRepository, InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public Product save(ProductForm form) {
        Product product = form.getId() == null ? new Product() : productRepository.findByIdForUpdate(form.getId())
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        BigDecimal previousStock = product.getCurrentStock() == null ? BigDecimal.ZERO : product.getCurrentStock();
        product.setCode(form.getCode().trim());
        product.setBarcode(blankToNull(form.getBarcode()));
        product.setName(form.getName().trim());
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
        return saved;
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
}
