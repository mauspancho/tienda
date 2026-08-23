package com.tienda.pos.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummary(
        BigDecimal todaySales,
        BigDecimal grossProfit,
        BigDecimal soldUnits,
        long tickets,
        long lowStockCount,
        BigDecimal todayExpenses,
        List<Object[]> dailySales,
        List<Object[]> topProducts
) {
}
