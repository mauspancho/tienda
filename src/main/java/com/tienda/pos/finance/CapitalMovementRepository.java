package com.tienda.pos.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CapitalMovementRepository extends JpaRepository<CapitalMovement, Long> {
    boolean existsByTenantIdAndType(Long tenantId, CapitalMovementType type);

    Page<CapitalMovement> findByTenantIdAndMovementDateBetweenOrderByMovementDateDesc(Long tenantId, LocalDate from, LocalDate to, Pageable pageable);

    Page<CapitalMovement> findByTenantIdAndTypeAndMovementDateBetweenOrderByMovementDateDesc(Long tenantId, CapitalMovementType type, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select coalesce(sum(c.amount), 0) from CapitalMovement c where c.tenant.id = :tenantId and c.type = :type")
    BigDecimal totalByType(@Param("tenantId") Long tenantId, @Param("type") CapitalMovementType type);

    @Query("select coalesce(sum(c.amount), 0) from CapitalMovement c where c.tenant.id = :tenantId and c.type = :type and c.movementDate between :from and :to")
    BigDecimal totalByTypeBetween(@Param("tenantId") Long tenantId, @Param("type") CapitalMovementType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select c.movementDate, c.type, coalesce(sum(c.amount), 0)
            from CapitalMovement c
            where c.tenant.id = :tenantId and c.movementDate between :from and :to
            group by c.movementDate, c.type
            order by c.movementDate
            """)
    List<Object[]> dailyCapitalTotals(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select year(c.movementDate), month(c.movementDate), c.type, coalesce(sum(c.amount), 0)
            from CapitalMovement c
            where c.tenant.id = :tenantId and c.movementDate between :from and :to
            group by year(c.movementDate), month(c.movementDate), c.type
            order by year(c.movementDate), month(c.movementDate)
            """)
    List<Object[]> monthlyCapitalTotals(@Param("tenantId") Long tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}