package com.tienda.pos.inventory;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@NormalMode
public class InventoryService {

    private static final EnumSet<InventoryMovementType> OUT_TYPES = EnumSet.of(
            InventoryMovementType.SALE,
            InventoryMovementType.PURCHASE_RETURN,
            InventoryMovementType.ADJUSTMENT_OUT
    );

    private static final EnumSet<InventoryMovementType> COST_UPDATE_TYPES = EnumSet.of(
            InventoryMovementType.PURCHASE,
            InventoryMovementType.ADJUSTMENT_IN,
            InventoryMovementType.INITIAL_STOCK
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

        BigDecimal unitCost = null;
        BigDecimal previousPurchaseCost = null;
        BigDecimal newPurchaseCost = null;
        BigDecimal costAdjustment = null;
        if (updatesCost(form.getMovementType(), delta)) {
            if (form.getUnitCost() == null || form.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Captura el costo unitario del producto.");
            }
            unitCost = MoneyUtils.money(form.getUnitCost());
            previousPurchaseCost = MoneyUtils.money(product.getPurchaseCost());
            newPurchaseCost = weightedAverageCost(previous, previousPurchaseCost, delta, unitCost);
            costAdjustment = MoneyUtils.money(newPurchaseCost.subtract(previousPurchaseCost).multiply(previous));
            product.setPurchaseCost(newPurchaseCost);
        }

        product.setCurrentStock(next);
        product.setUpdatedBy(CurrentUser.username());
        productRepository.save(product);
        createMovement(product, form.getMovementType(), delta, previous, next, "ADJUSTMENT", null, form.getNotes(),
                unitCost, previousPurchaseCost, newPurchaseCost, costAdjustment);
    }

    @Transactional
    public void reverseMovement(Long movementId) {
        InventoryMovement original = movementRepository.findDetailedById(movementId)
                .orElseThrow(() -> new DomainException("Movimiento no encontrado."));
        if (!original.isReversible()) {
            throw new DomainException("Este movimiento no se puede retirar desde inventario.");
        }
        Product product = productRepository.findByIdForUpdate(original.getProduct().getId())
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        if (changesCost(original) && movementRepository.existsNewerCostChangeForProduct(product.getId(), original.getCreatedAt())) {
            throw new DomainException("Retira primero los movimientos posteriores que cambiaron el costo de este producto.");
        }

        BigDecimal previous = product.getCurrentStock();
        BigDecimal reverseQuantity = original.getQuantity().negate();
        BigDecimal next = previous.add(reverseQuantity);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("No hay suficiente inventario para retirar este movimiento.");
        }

        BigDecimal previousPurchaseCost = null;
        BigDecimal newPurchaseCost = null;
        BigDecimal costAdjustment = null;
        if (changesCost(original)) {
            previousPurchaseCost = MoneyUtils.money(product.getPurchaseCost());
            newPurchaseCost = MoneyUtils.money(original.getPreviousPurchaseCost());
            costAdjustment = original.getCostAdjustment() == null ? BigDecimal.ZERO : MoneyUtils.money(original.getCostAdjustment().negate());
            product.setPurchaseCost(newPurchaseCost);
        }

        product.setCurrentStock(next);
        product.setUpdatedBy(CurrentUser.username());
        productRepository.save(product);

        InventoryMovement reversal = createMovement(product, reverseType(original.getMovementType()), reverseQuantity, previous, next,
                "REVERSAL", original.getId(), "Retiro del movimiento #" + original.getId(),
                original.getUnitCost(), previousPurchaseCost, newPurchaseCost, costAdjustment);
        original.setReversed(true);
        original.setReversedAt(LocalDateTime.now());
        original.setReversedBy(CurrentUser.username());
        original.setReversalMovementId(reversal.getId());
        original.setUpdatedBy(CurrentUser.username());
        movementRepository.save(original);
    }

    @Transactional
    public InventoryMovement createMovement(Product product, InventoryMovementType type, BigDecimal signedQuantity,
                                            BigDecimal previous, BigDecimal next, String referenceType, Long referenceId, String notes) {
        return createMovement(product, type, signedQuantity, previous, next, referenceType, referenceId, notes,
                null, null, null, null);
    }

    @Transactional
    public InventoryMovement createMovement(Product product, InventoryMovementType type, BigDecimal signedQuantity,
                                            BigDecimal previous, BigDecimal next, String referenceType, Long referenceId, String notes,
                                            BigDecimal unitCost, BigDecimal previousPurchaseCost, BigDecimal newPurchaseCost,
                                            BigDecimal costAdjustment) {
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setMovementType(type);
        movement.setQuantity(signedQuantity);
        movement.setPreviousStock(previous);
        movement.setNewStock(next);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNotes(notes);
        movement.setUnitCost(unitCost == null ? null : MoneyUtils.money(unitCost));
        movement.setPreviousPurchaseCost(previousPurchaseCost == null ? null : MoneyUtils.money(previousPurchaseCost));
        movement.setNewPurchaseCost(newPurchaseCost == null ? null : MoneyUtils.money(newPurchaseCost));
        movement.setCostAdjustment(costAdjustment == null ? null : MoneyUtils.money(costAdjustment));
        movement.setCreatedBy(CurrentUser.username());
        return movementRepository.save(movement);
    }

    public BigDecimal signedQuantity(InventoryMovementType type, BigDecimal quantity) {
        return OUT_TYPES.contains(type) ? quantity.abs().negate() : quantity.abs();
    }

    public BigDecimal weightedAverageCost(BigDecimal previousStock, BigDecimal previousCost,
                                          BigDecimal incomingQuantity, BigDecimal incomingCost) {
        BigDecimal stock = previousStock == null ? BigDecimal.ZERO : previousStock.max(BigDecimal.ZERO);
        BigDecimal cost = MoneyUtils.money(previousCost);
        BigDecimal quantity = incomingQuantity == null ? BigDecimal.ZERO : incomingQuantity.abs();
        BigDecimal unitCost = MoneyUtils.money(incomingCost);
        BigDecimal totalQuantity = stock.add(quantity);
        if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return unitCost;
        }
        BigDecimal totalValue = cost.multiply(stock).add(unitCost.multiply(quantity));
        return MoneyUtils.money(totalValue.divide(totalQuantity, 6, RoundingMode.HALF_UP));
    }

    private boolean updatesCost(InventoryMovementType type, BigDecimal delta) {
        return COST_UPDATE_TYPES.contains(type) && delta.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean changesCost(InventoryMovement movement) {
        return movement.getPreviousPurchaseCost() != null && movement.getNewPurchaseCost() != null;
    }

    private InventoryMovementType reverseType(InventoryMovementType type) {
        return switch (type) {
            case PURCHASE -> InventoryMovementType.PURCHASE_RETURN;
            case PURCHASE_RETURN -> InventoryMovementType.PURCHASE;
            case ADJUSTMENT_IN, INITIAL_STOCK -> InventoryMovementType.ADJUSTMENT_OUT;
            case ADJUSTMENT_OUT -> InventoryMovementType.ADJUSTMENT_IN;
            case SALE, SALE_RETURN -> throw new DomainException("Este movimiento no se puede retirar desde inventario.");
        };
    }
}
