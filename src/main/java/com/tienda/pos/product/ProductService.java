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

@Service
@NormalMode
public class ProductService {

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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
