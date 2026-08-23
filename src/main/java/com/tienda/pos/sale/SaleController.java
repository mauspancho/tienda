package com.tienda.pos.sale;

import com.tienda.pos.common.NormalMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@NormalMode
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class SaleController {

    private final SaleRepository saleRepository;

    public SaleController(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @GetMapping("/sales")
    public String list(Model model) {
        model.addAttribute("sales", saleRepository.findAllByOrderBySaleDateDesc(PageRequest.of(0, 100)));
        return "sales/index";
    }

    @GetMapping("/sales/{folio}")
    public String detail(@PathVariable String folio, Model model) {
        model.addAttribute("sale", saleRepository.findByFolio(folio).orElseThrow());
        return "sales/detail";
    }

    @GetMapping("/tickets/{folio}")
    public String ticket(@PathVariable String folio, Model model) {
        model.addAttribute("sale", saleRepository.findByFolio(folio).orElseThrow());
        return "tickets/show";
    }
}
