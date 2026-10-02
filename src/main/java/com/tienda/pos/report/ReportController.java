package com.tienda.pos.report;

import com.tienda.pos.common.NormalMode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports")
    public String index(@RequestParam(defaultValue = "TODAY") String period, Model model) {
        ReportSummary summary = reportService.summary(period);
        model.addAttribute("period", summary.period());
        model.addAttribute("sales", summary.sales());
        model.addAttribute("profit", summary.grossProfit());
        model.addAttribute("expenses", summary.expenses());
        model.addAttribute("result", summary.result());
        model.addAttribute("inventoryValue", summary.inventoryValue());
        model.addAttribute("topProducts", summary.topProducts());
        return "reports/index";
    }
}
