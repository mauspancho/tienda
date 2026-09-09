package com.tienda.pos.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    @EntityGraph(attributePaths = {"product"})
    Page<InventoryMovement> findByTenantIdAndProductIdOrderByCreatedAtDesc(Long tenantId, Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    Page<InventoryMovement> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse", "branch"})
    @Query("select m from InventoryMovement m where m.id = :id and m.tenant.id = :tenantId")
    Optional<InventoryMovement> findDetailedByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query("select coalesce(sum(m.costAdjustment), 0) from InventoryMovement m where m.tenant.id = :tenantId and m.createdAt between :start and :end")
    BigDecimal costAdjustmentBetween(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select count(m) > 0 from InventoryMovement m
            where m.tenant.id = :tenantId
              and m.product.id = :productId
              and m.createdAt > :createdAt
              and m.reversed = false
              and coalesce(m.referenceType, '') <> 'REVERSAL'
              and m.previousPurchaseCost is not null
              and m.newPurchaseCost is not null
            """)
    boolean existsNewerCostChangeForProduct(@Param("tenantId") Long tenantId, @Param("productId") Long productId, @Param("createdAt") LocalDateTime createdAt);
}