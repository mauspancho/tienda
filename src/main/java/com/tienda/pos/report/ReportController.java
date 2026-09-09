package com.tienda.pos.report;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.tenant.CurrentTenant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final ProductRepository productRepository;
    private final CurrentTenant currentTenant;

    public ReportController(SaleRepository saleRepository, ExpenseRepository expenseRepository,
                            ProductRepository productRepository, CurrentTenant currentTenant) {
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.productRepository = productRepository;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/reports")
    public String index(@RequestParam(defaultValue = "TODAY") String period, Model model) {
        Long tenantId = currentTenant.id();
        LocalDate start = switch (period) {
            case "YESTERDAY" -> LocalDate.now().minusDays(1);
            case "WEEK" -> LocalDate.now().minusDays(6);
            case "MONTH" -> LocalDate.now().withDayOfMonth(1);
            default -> LocalDate.now();
        };
        LocalDate end = "YESTERDAY".equals(period) ? start : LocalDate.now();
        var startDateTime = start.atStartOfDay();
        var endDateTime = end.plusDays(1).atStartOfDay().minusNanos(1);
        var sales = saleRepository.totalSales(tenantId, startDateTime, endDateTime);
        var profit = saleRepository.grossProfit(tenantId, startDateTime, endDateTime);
        var expenses = expenseRepository.totalBetween(tenantId, start, end);
        model.addAttribute("period", period);
        model.addAttribute("sales", sales);
        model.addAttribute("profit", profit);
        model.addAttribute("expenses", expenses);
        model.addAttribute("result", profit.subtract(expenses));
        model.addAttribute("inventoryValue", productRepository.inventoryValue(tenantId));
        model.addAttribute("topProducts", saleRepository.topProducts(tenantId, startDateTime, endDateTime, org.springframework.data.domain.PageRequest.of(0, 20)));
        return "reports/index";
    }
}