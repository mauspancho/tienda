package com.tienda.pos.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.sale.tenant.id = :tenantId and p.createdAt between :start and :end and p.sale.status = 'COMPLETED'")
    BigDecimal totalReceivedBetween(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
