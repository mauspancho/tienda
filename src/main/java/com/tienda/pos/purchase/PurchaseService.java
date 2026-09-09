package com.tienda.pos.purchase;

import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.inventory.InventoryStock;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUserRepository;
import com.tienda.pos.warehouse.Warehouse;
import com.tienda.pos.supplier.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
@NormalMode
public class PurchaseService {

    private static final DateTimeFormatter FOLIO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final InventoryService inventoryService;
    private final StoreContextService storeContextService;
    private final CurrentTenant currentTenant;

    public PurchaseService(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                           ProductRepository productRepository, AppUserRepository userRepository,
                           InventoryService inventoryService, StoreContextService storeContextService,
                           CurrentTenant currentTenant) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.storeContextService = storeContextService;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public Purchase register(PurchaseForm form) {
        Tenant tenant = currentTenant.get();
        Long tenantId = tenant.getId();
        Product product = productRepository.findByIdAndTenantIdForUpdate(form.getProductId(), tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        Warehouse warehouse = storeContextService.defaultWarehouse();
        InventoryStock stock = inventoryService.defaultStockForUpdate(product);
        BigDecimal unitCost = MoneyUtils.money(form.getUnitCost());
        BigDecimal subtotal = MoneyUtils.money(unitCost.multiply(form.getQuantity()));
        Purchase purchase = new Purchase();
        purchase.setTenant(tenant);
        purchase.setBranch(warehouse.getBranch());
        purchase.setWarehouse(warehouse);
        if (form.getSupplierId() != null) {
            purchase.setSupplier(supplierRepository.findByIdAndTenantId(form.getSupplierId(), tenantId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado.")));
        }
        purchase.setExternalFolio(normalizeFolio(form.getExternalFolio()));
        purchase.setFundingSource(form.getFundingSource());
        purchase.setNotes(form.getNotes());
        purchase.setSubtotal(subtotal);
        purchase.setTotal(subtotal);
        userRepository.findByUsernameWithTenant(CurrentUser.username())
                .filter(user -> user.getTenant() != null && user.getTenant().getId().equals(tenantId))
                .ifPresent(purchase::setUser);

        PurchaseItem item = new PurchaseItem();
        item.setProduct(product);
        item.setQuantity(form.getQuantity());
        item.setUnitCost(unitCost);
        item.setSubtotal(subtotal);
        purchase.addItem(item);
        Purchase saved = purchaseRepository.save(purchase);

        BigDecimal previous = stock.getQuantity();
        BigDecimal next = previous.add(form.getQuantity());
        BigDecimal previousPurchaseCost = MoneyUtils.money(product.getPurchaseCost());
        BigDecimal newPurchaseCost = previousPurchaseCost;
        BigDecimal costAdjustment = BigDecimal.ZERO;
        stock.setQuantity(next);
        stock.setMinimumStock(product.getMinimumStock());
        product.setCurrentStock(next);
        if (form.isUpdateProductCost()) {
            newPurchaseCost = inventoryService.weightedAverageCost(previous, previousPurchaseCost, form.getQuantity(), unitCost);
            costAdjustment = MoneyUtils.money(newPurchaseCost.subtract(previousPurchaseCost).multiply(previous));
            product.setPurchaseCost(newPurchaseCost);
        }
        productRepository.save(product);
        inventoryService.saveStock(stock);
        inventoryService.createMovement(product, InventoryMovementType.PURCHASE, form.getQuantity(), previous, next,
                warehouse, "PURCHASE", saved.getId(), "Compra confirmada " + saved.getExternalFolio(),
                unitCost, previousPurchaseCost, newPurchaseCost, costAdjustment);
        return saved;
    }

    String normalizeFolio(String externalFolio) {
        if (externalFolio != null && !externalFolio.isBlank()) {
            return externalFolio.trim();
        }
        return "COMP-" + LocalDate.now().format(FOLIO_DATE) + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}