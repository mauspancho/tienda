package com.tienda.pos.api.v1.report;

import com.tienda.pos.report.ReportService;
import com.tienda.pos.report.ReportSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ApiReportController {

    private final ReportService reportService;

    public ApiReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ReportResponse report(@RequestParam(defaultValue = "TODAY") String period) {
        ReportSummary summary = reportService.summary(period);
        List<TopProduct> products = summary.topProducts().stream()
                .map(row -> new TopProduct(String.valueOf(row[0]), decimal(row, 1), decimal(row, 2), decimal(row, 3)))
                .toList();
        return new ReportResponse(summary.period(), summary.from(), summary.to(), summary.sales(),
                summary.grossProfit(), summary.expenses(), summary.result(), summary.inventoryValue(), products);
    }

    private BigDecimal decimal(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) return BigDecimal.ZERO;
        return row[index] instanceof BigDecimal decimal ? decimal : new BigDecimal(row[index].toString());
    }

    public record ReportResponse(String period, LocalDate from, LocalDate to, BigDecimal sales,
                                 BigDecimal grossProfit, BigDecimal expenses, BigDecimal result,
                                 BigDecimal inventoryValue, List<TopProduct> topProducts) {
    }

    public record TopProduct(String product, BigDecimal quantity, BigDecimal sales, BigDecimal profit) {
    }
}
