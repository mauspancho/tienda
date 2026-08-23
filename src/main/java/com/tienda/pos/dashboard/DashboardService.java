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
        LocalDate today = LocalDate.now();
        var start = today.atStartOfDay();
        var end = today.plusDays(1).atStartOfDay().minusNanos(1);
        return new DashboardSummary(
                saleRepository.totalSales(start, end),
                saleRepository.grossProfit(start, end),
                saleRepository.soldUnits(start, end),
                saleRepository.countBySaleDateBetweenAndStatus(start, end, SaleStatus.COMPLETED),
                productRepository.findLowStock(PageRequest.of(0, 100)).size(),
                expenseRepository.totalBetween(today, today),
                productRepository.inventoryValue(),
                saleRepository.dailySalesSince(today.minusDays(6).atStartOfDay()),
                saleRepository.topProducts(start, end, PageRequest.of(0, 5))
        );
    }
}
