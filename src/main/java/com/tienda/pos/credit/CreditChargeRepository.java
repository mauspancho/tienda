package com.tienda.pos.credit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CreditChargeRepository extends JpaRepository<CreditCharge, Long> {
    @EntityGraph(attributePaths = {"customer", "user"})
    List<CreditCharge> findByTenantIdAndCustomerIdOrderByCreatedAtAsc(Long tenantId, Long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "user"})
    @Query("select c from CreditCharge c where c.tenant.id = :tenantId and c.id = :id")
    Optional<CreditCharge> findByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
    @Query("select coalesce(sum(c.amount), 0) from CreditCharge c where c.tenant.id = :tenantId and c.status = 'ACTIVE'")
    BigDecimal totalActiveCharges(@Param("tenantId") Long tenantId);
    Optional<CreditCharge> findByIdAndTenantId(Long id, Long tenantId);
}
