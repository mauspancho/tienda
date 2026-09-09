package com.tienda.pos.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    @Query("select coalesce(sum(m.amount), 0) from CashMovement m where m.tenant.id = :tenantId and m.cashRegisterSession.id = :sessionId")
    BigDecimal expectedAmount(@Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId);
}