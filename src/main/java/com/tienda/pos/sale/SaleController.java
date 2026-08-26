package com.tienda.pos.sale;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
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

    public SaleController(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @GetMapping("/sales")
    public String list(Model model) {
        var pageRequest = PageRequest.of(0, 100);
        var sales = CurrentUser.hasRole("ROLE_ADMIN")
                ? saleRepository.findAllByOrderBySaleDateDesc(pageRequest)
                : saleRepository.findByCashierUsernameOrderBySaleDateDesc(CurrentUser.username(), pageRequest);
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
        return (CurrentUser.hasRole("ROLE_ADMIN")
                ? saleRepository.findByFolio(folio)
                : saleRepository.findByFolioAndCashierUsername(folio, CurrentUser.username()))
                .orElseThrow(() -> new DomainException("Venta no disponible para este usuario."));
    }
}
