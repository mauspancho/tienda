package com.tienda.pos.dashboard;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.purchase.PurchaseRepository;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.tenant.CurrentTenant;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CurrentTenant currentTenant;

    public DashboardController(DashboardService dashboardService, ProductRepository productRepository,
                               SaleRepository saleRepository, PurchaseRepository purchaseRepository,
                               CurrentTenant currentTenant) {
        this.dashboardService = dashboardService;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.currentTenant = currentTenant;
    }

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        Long tenantId = currentTenant.id();
        boolean admin = CurrentUser.hasRole("ROLE_ADMIN");
        String username = CurrentUser.username();
        var latestSales = admin
                ? saleRepository.findByTenantIdOrderBySaleDateDesc(tenantId, PageRequest.of(0, 8)).getContent()
                : saleRepository.findByTenantIdAndCashierUsernameOrderBySaleDateDesc(tenantId, username, PageRequest.of(0, 8)).getContent();

        model.addAttribute("summary", dashboardService.today(username, admin));
        model.addAttribute("lowStock", productRepository.findLowStock(tenantId, PageRequest.of(0, 8)));
        model.addAttribute("latestSales", latestSales);
        model.addAttribute("latestPurchases", purchaseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 8)).getContent());
        model.addAttribute("profitDate", LocalDate.now());
        return "dashboard/index";
    }

    @GetMapping("/api/dashboard/summary")
    @ResponseBody
    public DashboardSummary summary() {
        return dashboardService.today(CurrentUser.username(), CurrentUser.hasRole("ROLE_ADMIN"));
    }

    @GetMapping("/api/dashboard/profit")
    @ResponseBody
    public DashboardProfitAnalysis profit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @RequestParam(defaultValue = "day") String period) {
        return dashboardService.profitAnalysis(date, period, CurrentUser.username(), CurrentUser.hasRole("ROLE_ADMIN"));
    }
}