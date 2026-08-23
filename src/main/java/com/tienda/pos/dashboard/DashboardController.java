package com.tienda.pos.dashboard;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.purchase.PurchaseRepository;
import com.tienda.pos.sale.SaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@NormalMode
public class DashboardController {

    private final DashboardService dashboardService;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;

    public DashboardController(DashboardService dashboardService, ProductRepository productRepository,
                               SaleRepository saleRepository, PurchaseRepository purchaseRepository) {
        this.dashboardService = dashboardService;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("summary", dashboardService.today());
        model.addAttribute("lowStock", productRepository.findLowStock(PageRequest.of(0, 8)));
        model.addAttribute("latestSales", saleRepository.findAllByOrderBySaleDateDesc(PageRequest.of(0, 8)).getContent());
        model.addAttribute("latestPurchases", purchaseRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 8)).getContent());
        return "dashboard/index";
    }

    @GetMapping("/api/dashboard/summary")
    @ResponseBody
    public DashboardSummary summary() {
        return dashboardService.today();
    }
}
