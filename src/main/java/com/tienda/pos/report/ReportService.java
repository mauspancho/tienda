package com.tienda.pos.report;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.sale.SaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@NormalMode
public class ReportService {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final ProductRepository productRepository;

    public ReportService(SaleRepository saleRepository, ExpenseRepository expenseRepository,
                         ProductRepository productRepository, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.productRepository = productRepository;
    }

    public ReportSummary summary(String requestedPeriod) {
        String period = requestedPeriod == null ? "TODAY" : requestedPeriod;
        LocalDate start = switch (period) {
            case "YESTERDAY" -> LocalDate.now().minusDays(1);
            case "WEEK" -> LocalDate.now().minusDays(6);
            case "MONTH" -> LocalDate.now().withDayOfMonth(1);
            default -> LocalDate.now();
        };
        LocalDate end = "YESTERDAY".equals(period) ? start : LocalDate.now();
        var startDateTime = start.atStartOfDay();
        var endDateTime = end.plusDays(1).atStartOfDay().minusNanos(1);
        var sales = saleRepository.totalSales(currentTenant.id(), startDateTime, endDateTime);
        var profit = saleRepository.grossProfit(currentTenant.id(), startDateTime, endDateTime);
        var expenses = expenseRepository.totalBetween(currentTenant.id(), start, end);
        return new ReportSummary(period, start, end, sales, profit, expenses, profit.subtract(expenses),
                productRepository.inventoryValue(currentTenant.id()), saleRepository.topProducts(currentTenant.id(), startDateTime, endDateTime,
                PageRequest.of(0, 20)));
    }
}
