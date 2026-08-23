package com.tienda.pos.inventory;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;

@Service
@NormalMode
public class InventoryService {

    private static final EnumSet<InventoryMovementType> OUT_TYPES = EnumSet.of(
            InventoryMovementType.SALE,
            InventoryMovementType.PURCHASE_RETURN,
            InventoryMovementType.ADJUSTMENT_OUT
    );

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(ProductRepository productRepository, InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public void adjust(InventoryAdjustmentForm form) {
        Product product = productRepository.findByIdForUpdate(form.getProductId())
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        BigDecimal delta = signedQuantity(form.getMovementType(), form.getQuantity());
        BigDecimal previous = product.getCurrentStock();
        BigDecimal next = previous.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("No hay suficiente inventario.");
        }
        product.setCurrentStock(next);
        product.setUpdatedBy(CurrentUser.username());
        productRepository.save(product);
        createMovement(product, form.getMovementType(), delta, previous, next, "ADJUSTMENT", null, form.getNotes());
    }

    @Transactional
    public void createMovement(Product product, InventoryMovementType type, BigDecimal signedQuantity,
                               BigDecimal previous, BigDecimal next, String referenceType, Long referenceId, String notes) {
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setMovementType(type);
        movement.setQuantity(signedQuantity);
        movement.setPreviousStock(previous);
        movement.setNewStock(next);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNotes(notes);
        movement.setCreatedBy(CurrentUser.username());
        movementRepository.save(movement);
    }

    public BigDecimal signedQuantity(InventoryMovementType type, BigDecimal quantity) {
        return OUT_TYPES.contains(type) ? quantity.abs().negate() : quantity.abs();
    }
}
