package com.tienda.pos.dashboard;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.sale.SaleStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@NormalMode
public class DashboardService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ExpenseRepository expenseRepository;

    public DashboardService(SaleRepository saleRepository, ProductRepository productRepository,
                            ExpenseRepository expenseRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.expenseRepository = expenseRepository;
    }

    public DashboardSummary today() {
        return today(null, true);
    }

    public DashboardSummary today(String username, boolean admin) {
        LocalDate today = LocalDate.now();
        var start = today.atStartOfDay();
        var end = today.plusDays(1).atStartOfDay().minusNanos(1);
        return new DashboardSummary(
                admin ? saleRepository.totalSales(start, end) : saleRepository.totalSalesByCashier(username, start, end),
                admin ? saleRepository.grossProfit(start, end) : saleRepository.grossProfitByCashier(username, start, end),
                admin ? saleRepository.soldUnits(start, end) : saleRepository.soldUnitsByCashier(username, start, end),
                admin ? saleRepository.countBySaleDateBetweenAndStatus(start, end, SaleStatus.COMPLETED)
                        : saleRepository.countByCashierUsernameAndSaleDateBetweenAndStatus(username, start, end, SaleStatus.COMPLETED),
                productRepository.findLowStock(PageRequest.of(0, 100)).size(),
                expenseRepository.totalBetween(today, today),
                productRepository.inventoryValue(),
                admin ? saleRepository.dailySalesSince(today.minusDays(6).atStartOfDay())
                        : saleRepository.dailySalesSinceByCashier(username, today.minusDays(6).atStartOfDay()),
                admin ? saleRepository.topProducts(start, end, PageRequest.of(0, 5))
                        : saleRepository.topProductsByCashier(username, start, end, PageRequest.of(0, 5))
        );
    }
}