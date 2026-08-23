package com.tienda.pos.purchase;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@NormalMode
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final InventoryService inventoryService;

    public PurchaseService(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                           ProductRepository productRepository, AppUserRepository userRepository,
                           InventoryService inventoryService) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Purchase register(PurchaseForm form) {
        Product product = productRepository.findByIdForUpdate(form.getProductId())
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        BigDecimal unitCost = MoneyUtils.money(form.getUnitCost());
        BigDecimal subtotal = MoneyUtils.money(unitCost.multiply(form.getQuantity()));
        Purchase purchase = new Purchase();
        purchase.setSupplier(supplierRepository.findById(form.getSupplierId())
                .orElseThrow(() -> new DomainException("Proveedor no encontrado.")));
        purchase.setExternalFolio(form.getExternalFolio());
        purchase.setNotes(form.getNotes());
        purchase.setSubtotal(subtotal);
        purchase.setTotal(subtotal);
        userRepository.findByUsername(CurrentUser.username()).ifPresent(purchase::setUser);

        PurchaseItem item = new PurchaseItem();
        item.setProduct(product);
        item.setQuantity(form.getQuantity());
        item.setUnitCost(unitCost);
        item.setSubtotal(subtotal);
        purchase.addItem(item);
        Purchase saved = purchaseRepository.save(purchase);

        BigDecimal previous = product.getCurrentStock();
        BigDecimal next = previous.add(form.getQuantity());
        product.setCurrentStock(next);
        if (form.isUpdateProductCost()) {
            product.setPurchaseCost(unitCost);
        }
        productRepository.save(product);
        inventoryService.createMovement(product, InventoryMovementType.PURCHASE, form.getQuantity(), previous, next,
                "PURCHASE", saved.getId(), "Compra confirmada");
        return saved;
    }
}
