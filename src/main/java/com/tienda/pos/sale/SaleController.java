package com.tienda.pos.sale;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class SaleController {

    private final SaleRepository saleRepository;
    private final CurrentTenant currentTenant;

    public SaleController(SaleRepository saleRepository, CurrentTenant currentTenant) {
        this.saleRepository = saleRepository;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/sales")
    public String list(Model model) {
        Long tenantId = currentTenant.id();
        var pageRequest = PageRequest.of(0, 100);
        var sales = CurrentUser.hasRole("ROLE_ADMIN")
                ? saleRepository.findByTenantIdOrderBySaleDateDesc(tenantId, pageRequest)
                : saleRepository.findByTenantIdAndCashierUsernameOrderBySaleDateDesc(tenantId, CurrentUser.username(), pageRequest);
        model.addAttribute("sales", sales);
        return "sales/index";
    }

    @GetMapping("/sales/{folio}")
    public String detail(@PathVariable String folio, Model model) {
        model.addAttribute("sale", visibleSale(folio));
        return "sales/detail";
    }

    @GetMapping("/tickets/{folio}")
    public String ticket(@PathVariable String folio, Model model) {
        model.addAttribute("sale", visibleSale(folio));
        return "tickets/show";
    }

    private Sale visibleSale(String folio) {
        Long tenantId = currentTenant.id();
        return (CurrentUser.hasRole("ROLE_ADMIN")
                ? saleRepository.findByTenantIdAndFolio(tenantId, folio)
                : saleRepository.findByTenantIdAndFolioAndCashierUsername(tenantId, folio, CurrentUser.username()))
                .orElseThrow(() -> new DomainException("Venta no disponible para este usuario."));
    }
}