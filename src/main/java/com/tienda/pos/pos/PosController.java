package com.tienda.pos.pos;

import com.tienda.pos.cash.CashRegisterSessionRepository;
import com.tienda.pos.cash.CashService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.sale.SaleRequest;
import com.tienda.pos.sale.SaleResult;
import com.tienda.pos.sale.SaleService;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class PosController {

    private final SaleService saleService;
    private final CashService cashService;
    private final CashRegisterSessionRepository cashRegisterSessionRepository;
    private final AppUserRepository userRepository;

    public PosController(SaleService saleService, CashService cashService,
                         CashRegisterSessionRepository cashRegisterSessionRepository,
                         AppUserRepository userRepository) {
        this.saleService = saleService;
        this.cashService = cashService;
        this.cashRegisterSessionRepository = cashRegisterSessionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/pos")
    public String pos(Model model) {
        AppUser cashier = userRepository.findByUsername(CurrentUser.username()).orElseThrow();
        var currentCashSession = cashRegisterSessionRepository.findByCashierAndOpenTrue(cashier).orElse(null);
        model.addAttribute("currentCashSession", currentCashSession);
        model.addAttribute("cashOpen", currentCashSession != null);
        return "pos/index";
    }

    @PostMapping("/pos/cash/open")
    public String openCash(@RequestParam(defaultValue = "0") BigDecimal openingAmount,
                           RedirectAttributes redirectAttributes) {
        cashService.open(CurrentUser.username(), openingAmount);
        redirectAttributes.addFlashAttribute("success", "Caja abierta. Ya puedes vender.");
        return "redirect:/admin/pos";
    }

    @PostMapping("/pos/cash/close")
    public String closeCash(@RequestParam Long sessionId, @RequestParam BigDecimal countedAmount,
                            RedirectAttributes redirectAttributes) {
        cashService.close(CurrentUser.username(), sessionId, countedAmount);
        redirectAttributes.addFlashAttribute("success", "Caja cerrada. Las ventas quedan bloqueadas hasta abrir otra caja.");
        return "redirect:/admin/pos";
    }

    @PostMapping("/pos/checkout")
    @ResponseBody
    public SaleResult checkout(@Valid @RequestBody SaleRequest request) {
        return saleService.checkout(request);
    }
}
