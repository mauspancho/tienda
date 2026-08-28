package com.tienda.pos.inventory;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
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
        InventoryService service = new InventoryService(null, null);

        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_IN, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("3"));
        assertThat(service.signedQuantity(InventoryMovementType.ADJUSTMENT_OUT, new BigDecimal("3")))
                .isEqualByComparingTo(new BigDecimal("-3"));
        assertThat(service.signedQuantity(InventoryMovementType.SALE, new BigDecimal("2")))
                .isEqualByComparingTo(new BigDecimal("-2"));
    }

    @Test
    void calculatesWeightedAverageCostForIncomingStock() {
        InventoryService service = new InventoryService(null, null);

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
        ProductRepository productRepository = mock(ProductRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        InventoryService service = new InventoryService(productRepository, movementRepository);
        Product product = new Product();
        product.setId(5L);
        product.setCurrentStock(new BigDecimal("15.000"));
        product.setPurchaseCost(new BigDecimal("16.00"));
        InventoryMovement original = new InventoryMovement();
        original.setId(10L);
        original.setProduct(product);
        original.setMovementType(InventoryMovementType.ADJUSTMENT_IN);
        original.setQuantity(new BigDecimal("5.000"));
        original.setPreviousStock(new BigDecimal("10.000"));
        original.setNewStock(new BigDecimal("15.000"));
        original.setUnitCost(new BigDecimal("14.00"));
        original.setPreviousPurchaseCost(new BigDecimal("17.00"));
        original.setNewPurchaseCost(new BigDecimal("16.00"));
        original.setCostAdjustment(new BigDecimal("-10.00"));
        stampCreatedAt(original, LocalDateTime.now().minusMinutes(5));

        when(movementRepository.findDetailedById(10L)).thenReturn(Optional.of(original));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        when(movementRepository.existsNewerCostChangeForProduct(eq(5L), any(LocalDateTime.class))).thenReturn(false);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement movement = invocation.getArgument(0);
            if (movement.getId() == null) {
                movement.setId(99L);
            }
            return movement;
        });

        service.reverseMovement(10L);

        assertThat(product.getCurrentStock()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(product.getPurchaseCost()).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(original.isReversed()).isTrue();
        assertThat(original.getReversalMovementId()).isEqualTo(99L);
        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, times(2)).save(movementCaptor.capture());
        InventoryMovement reversal = movementCaptor.getAllValues().get(0);
        assertThat(reversal.getMovementType()).isEqualTo(InventoryMovementType.ADJUSTMENT_OUT);
        assertThat(reversal.getQuantity()).isEqualByComparingTo(new BigDecimal("-5.000"));
        assertThat(reversal.getReferenceType()).isEqualTo("REVERSAL");
        assertThat(reversal.getReferenceId()).isEqualTo(10L);
        assertThat(reversal.getCostAdjustment()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void doesNotReverseSaleMovementsFromInventory() {
        ProductRepository productRepository = mock(ProductRepository.class);
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        InventoryService service = new InventoryService(productRepository, movementRepository);
        InventoryMovement saleMovement = new InventoryMovement();
        saleMovement.setId(20L);
        saleMovement.setMovementType(InventoryMovementType.SALE);

        when(movementRepository.findDetailedById(20L)).thenReturn(Optional.of(saleMovement));

        assertThatThrownBy(() -> service.reverseMovement(20L))
                .hasMessage("Este movimiento no se puede retirar desde inventario.");
        verifyNoInteractions(productRepository);
    }

    private void stampCreatedAt(BaseEntity entity, LocalDateTime createdAt) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(entity, createdAt);
    }
}
