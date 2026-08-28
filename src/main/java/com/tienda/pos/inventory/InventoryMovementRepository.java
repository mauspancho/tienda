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
    Page<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    @EntityGraph(attributePaths = {"product"})
    Page<InventoryMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    @Query("select m from InventoryMovement m where m.id = :id")
    Optional<InventoryMovement> findDetailedById(@Param("id") Long id);

    @Query("select coalesce(sum(m.costAdjustment), 0) from InventoryMovement m where m.createdAt between :start and :end")
    BigDecimal costAdjustmentBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select count(m) > 0 from InventoryMovement m
            where m.product.id = :productId
              and m.createdAt > :createdAt
              and m.reversed = false
              and coalesce(m.referenceType, '') <> 'REVERSAL'
              and m.previousPurchaseCost is not null
              and m.newPurchaseCost is not null
            """)
    boolean existsNewerCostChangeForProduct(@Param("productId") Long productId, @Param("createdAt") LocalDateTime createdAt);
}
