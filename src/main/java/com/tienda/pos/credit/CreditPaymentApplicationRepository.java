package com.tienda.pos.credit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface CreditPaymentApplicationRepository extends JpaRepository<CreditPaymentApplication, Long> {
    @Query("select coalesce(sum(a.amount), 0) from CreditPaymentApplication a where a.tenant.id = :tenantId and a.sale.id = :saleId and a.payment.status = 'ACTIVE'")
    BigDecimal activeAppliedToSale(@Param("tenantId") Long tenantId, @Param("saleId") Long saleId);
    @Query("select count(a) > 0 from CreditPaymentApplication a where a.tenant.id = :tenantId and a.sale.id = :saleId and a.payment.status = 'ACTIVE'")
    boolean existsActiveBySaleId(@Param("tenantId") Long tenantId, @Param("saleId") Long saleId);
    @Query("select coalesce(sum(a.amount), 0) from CreditPaymentApplication a where a.tenant.id = :tenantId and a.charge.id = :chargeId and a.payment.status = 'ACTIVE'")
    BigDecimal activeAppliedToCharge(@Param("tenantId") Long tenantId, @Param("chargeId") Long chargeId);
    @Query("select count(a) > 0 from CreditPaymentApplication a where a.tenant.id = :tenantId and a.charge.id = :chargeId and a.payment.status = 'ACTIVE'")
    boolean existsActiveByChargeId(@Param("tenantId") Long tenantId, @Param("chargeId") Long chargeId);
}
