package com.tienda.pos.cash;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class CashController {

    private final CashRegisterSessionRepository sessionRepository;
    private final CashService cashService;

    public CashController(CashRegisterSessionRepository sessionRepository, CashService cashService) {
        this.sessionRepository = sessionRepository;
        this.cashService = cashService;
    }

    @GetMapping("/cash")
    public String cash(Model model) {
        model.addAttribute("sessions", sessionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)));
        return "cash/index";
    }

    @PostMapping("/cash/open")
    public String open(@RequestParam BigDecimal openingAmount, RedirectAttributes redirectAttributes) {
        cashService.open(CurrentUser.username(), openingAmount);
        redirectAttributes.addFlashAttribute("success", "Caja abierta.");
        return "redirect:/admin/cash";
    }

    @PostMapping("/cash/close")
    public String close(@RequestParam Long sessionId, @RequestParam BigDecimal countedAmount,
                        RedirectAttributes redirectAttributes) {
        cashService.close(CurrentUser.username(), sessionId, countedAmount);
        redirectAttributes.addFlashAttribute("success", "Caja cerrada.");
        return "redirect:/admin/cash";
    }
}
