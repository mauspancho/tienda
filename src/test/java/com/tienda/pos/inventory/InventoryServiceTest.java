package com.tienda.pos.inventory;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.warehouse.Warehouse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    @Test
    void signsInventoryQuantities() {
        InventoryService service = new InventoryService(null, null, null, null, null);

        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_IN, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("3"));
        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_OUT, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("-3"));
        assertThat(service.signedQuantity(InventoryMovementType.SALE, new BigDecimal("2")))
                .isEqualByComparingTo(new BigDecimal("-2"));
    }

    @Test
    void calculatesWeightedAverageCostForIncomingStock() {
        InventoryService service = new InventoryService(null, null, null, null, null);

        BigDecimal average = service.weightedAverageCost(
                new BigDecimal("10.000"),
                new BigDecimal("17.00"),
                new BigDecimal("10.000"),
                new BigDecimal("14.80")
        );

        assertThat(average).isEqualByComparingTo(new BigDecimal("15.90"));
    }

    @Test
    void reversesInventoryMovementAndRestoresPreviousCost() throws Exception {
        Tenant tenant = tenant();
        ProductRepository productRepository = mock(ProductRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        InventoryStockRepository stockRepository = mock(InventoryStockRepository.class);
        StoreContextService storeContextService = mock(StoreContextService.class);
        CurrentTenant currentTenant = currentTenant(tenant);
        InventoryService service = new InventoryService(productRepository, movementRepository, stockRepository, storeContextService, currentTenant);
        Product product = product(tenant);
        Branch branch = new Branch();
        branch.setTenant(tenant);
        Warehouse warehouse = new Warehouse();
        warehouse.setTenant(tenant);
        warehouse.setBranch(branch);
        InventoryStock stock = new InventoryStock();
        stock.setTenant(tenant);
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(new BigDecimal("15.000"));
        stock.setMinimumStock(BigDecimal.ZERO);
        InventoryMovement original = new InventoryMovement();
        original.setId(10L);
        original.setTenant(tenant);
        original.setProduct(product);
        original.setWarehouse(warehouse);
        original.setBranch(branch);
        original.setMovementType(InventoryMovementType.ADJUSTMENT_IN);
        original.setQuantity(new BigDecimal("5.000"));
        original.setPreviousStock(new BigDecimal("10.000"));
        original.setNewStock(new BigDecimal("15.000"));
        original.setUnitCost(new BigDecimal("14.00"));
        original.setPreviousPurchaseCost(new BigDecimal("17.00"));
        original.setNewPurchaseCost(new BigDecimal("16.00"));
        original.setCostAdjustment(new BigDecimal("-10.00"));
        stampCreatedAt(original, LocalDateTime.now().minusMinutes(5));

        when(movementRepository.findDetailedByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(original));
        when(productRepository.findByIdAndTenantIdForUpdate(5L, 1L)).thenReturn(Optional.of(product));
        when(stockRepository.findByProductAndWarehouseAndTenantIdForUpdate(product, warehouse, 1L)).thenReturn(Optional.of(stock));
        when(movementRepository.existsNewerCostChangeForProduct(eq(1L), eq(5L), any(LocalDateTime.class))).thenReturn(false);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement movement = invocation.getArgument(0);
            if (movement.getId() == null) {
                movement.setId(99L);
            }
            return movement;
        });

        service.reverseMovement(10L);

        assertThat(product.getCurrentStock()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(stock.getQuantity()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(product.getPurchaseCost()).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(original.isReversed()).isTrue();
        assertThat(original.getReversalMovementId()).isEqualTo(99L);
        verify(stockRepository).save(stock);
        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, times(2)).save(movementCaptor.capture());
        InventoryMovement reversal = movementCaptor.getAllValues().get(0);
        assertThat(reversal.getTenant()).isSameAs(tenant);
        assertThat(reversal.getWarehouse()).isSameAs(warehouse);
        assertThat(reversal.getBranch()).isSameAs(branch);
        assertThat(reversal.getMovementType()).isEqualTo(InventoryMovementType.ADJUSTMENT_OUT);
        assertThat(reversal.getQuantity()).isEqualByComparingTo(new BigDecimal("-5.000"));
        assertThat(reversal.getReferenceType()).isEqualTo("REVERSAL");
        assertThat(reversal.getReferenceId()).isEqualTo(10L);
        assertThat(reversal.getCostAdjustment()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void doesNotReverseSaleMovementsFromInventory() {
        Tenant tenant = tenant();
        ProductRepository productRepository = mock(ProductRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        CurrentTenant currentTenant = currentTenant(tenant);
        InventoryService service = new InventoryService(productRepository, movementRepository, null, null, currentTenant);
        InventoryMovement saleMovement = new InventoryMovement();
        saleMovement.setId(20L);
        saleMovement.setTenant(tenant);
        saleMovement.setMovementType(InventoryMovementType.SALE);

        when(movementRepository.findDetailedByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(saleMovement));

        assertThatThrownBy(() -> service.reverseMovement(20L))
                .hasMessage("Este movimiento no se puede retirar desde inventario.");
        verifyNoInteractions(productRepository);
    }

    private CurrentTenant currentTenant(Tenant tenant) {
        CurrentTenant currentTenant = mock(CurrentTenant.class);
        when(currentTenant.get()).thenReturn(tenant);
        when(currentTenant.id()).thenReturn(tenant.getId());
        return currentTenant;
    }

    private Product product(Tenant tenant) {
        Product product = new Product();
        product.setId(5L);
        product.setTenant(tenant);
        product.setCurrentStock(new BigDecimal("15.000"));
        product.setMinimumStock(BigDecimal.ZERO);
        product.setPurchaseCost(new BigDecimal("16.00"));
        return product;
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCode("default");
        tenant.setName("Tienda");
        tenant.setActive(true);
        return tenant;
    }

    private void stampCreatedAt(BaseEntity entity, LocalDateTime createdAt) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, createdAt);
    }
}