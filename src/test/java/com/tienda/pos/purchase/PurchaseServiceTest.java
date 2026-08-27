package com.tienda.pos.purchase;

import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.UnitType;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseServiceTest {

    private final PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final PurchaseService service = new PurchaseService(purchaseRepository, supplierRepository,
            productRepository, userRepository, inventoryService);

    @Test
    void registersPurchaseWithoutSupplierAndAutoAssignsFolio() {
        Product product = new Product();
        product.setId(5L);
        product.setCode("PRD-00000005");
        product.setName("Coca cola");
        product.setCurrentStock(new BigDecimal("24.000"));
        product.setPurchaseCost(new BigDecimal("16.87"));
        product.setSalePrice(new BigDecimal("20.00"));
        product.setMinimumStock(BigDecimal.ZERO);
        product.setUnit(UnitType.PIEZA);

        PurchaseForm form = new PurchaseForm();
        form.setProductId(5L);
        form.setQuantity(new BigDecimal("12.000"));
        form.setUnitCost(new BigDecimal("17.50"));
        form.setExternalFolio(" ");
        form.setUpdateProductCost(true);

        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        when(inventoryService.weightedAverageCost(new BigDecimal("24.000"), new BigDecimal("16.87"),
                new BigDecimal("12.000"), new BigDecimal("17.50"))).thenReturn(new BigDecimal("17.08"));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(22L);
            return purchase;
        });

        Purchase saved = service.register(form);

        assertThat(saved.getSupplier()).isNull();
        assertThat(saved.getExternalFolio()).startsWith("COMP-");
        assertThat(saved.getTotal()).isEqualByComparingTo(new BigDecimal("210.00"));
        assertThat(product.getCurrentStock()).isEqualByComparingTo(new BigDecimal("36.000"));
        assertThat(product.getPurchaseCost()).isEqualByComparingTo(new BigDecimal("17.08"));
        verifyNoInteractions(supplierRepository);
        ArgumentCaptor<BigDecimal> quantityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(inventoryService).createMovement(any(Product.class), any(InventoryMovementType.class),
                quantityCaptor.capture(), any(BigDecimal.class), any(BigDecimal.class), any(String.class), any(Long.class),
                any(String.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class));
        assertThat(quantityCaptor.getValue()).isEqualByComparingTo(new BigDecimal("12.000"));
    }
}

