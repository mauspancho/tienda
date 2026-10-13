package com.tienda.pos.credit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CreditChargeRepository extends JpaRepository<CreditCharge, Long> {
    @EntityGraph(attributePaths = {"customer", "user"})
    List<CreditCharge> findByCustomerIdOrderByCreatedAtAsc(Long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "user"})
    @Query("select c from CreditCharge c where c.id = :id")
    Optional<CreditCharge> findByIdForUpdate(@Param("id") Long id);
    @Query("select coalesce(sum(c.amount), 0) from CreditCharge c where c.status = 'ACTIVE'")
    BigDecimal totalActiveCharges();
}
