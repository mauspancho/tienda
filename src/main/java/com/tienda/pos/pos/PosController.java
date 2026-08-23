package com.tienda.pos.pos;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.sale.SaleRequest;
import com.tienda.pos.sale.SaleResult;
import com.tienda.pos.sale.SaleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@NormalMode
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class PosController {

    private final SaleService saleService;

    public PosController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping("/pos")
    public String pos() {
        return "pos/index";
    }

    @PostMapping("/pos/checkout")
    @ResponseBody
    public SaleResult checkout(@Valid @RequestBody SaleRequest request) {
        return saleService.checkout(request);
    }
}
