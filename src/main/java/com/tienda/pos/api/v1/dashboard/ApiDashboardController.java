package com.tienda.pos.api.v1.dashboard;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.dashboard.DashboardProfitAnalysis;
import com.tienda.pos.dashboard.DashboardService;
import com.tienda.pos.dashboard.DashboardSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiDashboardController {

    private final DashboardService dashboardService;

    public ApiDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        DashboardSummary summary = dashboardService.today(CurrentUser.username(), CurrentUser.hasRole("ROLE_ADMIN"));
        List<DailySale> daily = summary.dailySales().stream()
                .map(row -> new DailySale(String.valueOf(row[0]), decimal(row, 1))).toList();
        List<TopProduct> top = summary.topProducts().stream()
                .map(row -> new TopProduct(String.valueOf(row[0]), decimal(row, 1), decimal(row, 2), decimal(row, 3)))
                .toList();
        return new DashboardResponse(summary.todaySales(), summary.grossProfit(), summary.soldUnits(),
                summary.tickets(), summary.lowStockCount(), summary.todayExpenses(), summary.inventoryInvestment(),
                daily, top);
    }

    @GetMapping("/profit")
    public DashboardProfitAnalysis profit(@RequestParam(required = false) LocalDate date,
                                          @RequestParam(defaultValue = "day") String period) {
        return dashboardService.profitAnalysis(date, period, CurrentUser.username(), CurrentUser.hasRole("ROLE_ADMIN"));
    }

    private static BigDecimal decimal(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) return BigDecimal.ZERO;
        Object value = row[index];
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    public record DashboardResponse(BigDecimal todaySales, BigDecimal grossProfit, BigDecimal soldUnits,
                                    long tickets, long lowStockCount, BigDecimal todayExpenses,
                                    BigDecimal inventoryInvestment, List<DailySale> dailySales,
                                    List<TopProduct> topProducts) {
    }

    public record DailySale(String date, BigDecimal sales) {
    }

    public record TopProduct(String product, BigDecimal quantity, BigDecimal sales, BigDecimal profit) {
    }
}
