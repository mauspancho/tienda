package com.tienda.pos.credit;

import com.tienda.pos.customer.Customer;
import com.tienda.pos.sale.Sale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CreditViews {
    private CreditViews() {}
    public record Summary(BigDecimal pending, long customersWithDebt, BigDecimal totalGranted, BigDecimal totalPaid) {}
    public record CustomerRow(Customer customer, BigDecimal balance, BigDecimal totalGranted,
                              BigDecimal totalPaid, LocalDateTime lastActivity) {}
    public record SaleRow(Sale sale, BigDecimal paid, BigDecimal balance) {}
    public record ChargeRow(CreditCharge charge, BigDecimal paid, BigDecimal balance) {}
    public record Movement(LocalDateTime date, String type, String reference, String detail,
                           BigDecimal charge, BigDecimal payment, BigDecimal resultingBalance,
                           String username, boolean reversed, Long entityId) {}
    public record Statement(Customer customer, BigDecimal balance, BigDecimal totalGranted,
                            BigDecimal totalPaid, List<SaleRow> sales, List<ChargeRow> charges, List<CreditPayment> payments,
                            List<Movement> movements) {}
}
