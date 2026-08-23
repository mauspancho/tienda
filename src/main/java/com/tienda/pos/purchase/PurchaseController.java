package com.tienda.pos.purchase;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.supplier.SupplierRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseController {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseController(PurchaseRepository purchaseRepository, PurchaseService purchaseService,
                              SupplierRepository supplierRepository, ProductRepository productRepository) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseService = purchaseService;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/purchases")
    public String list(Model model) {
        model.addAttribute("purchases", purchaseRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)));
        model.addAttribute("purchaseForm", new PurchaseForm());
        model.addAttribute("suppliers", supplierRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("products", productRepository.findAll(PageRequest.of(0, 500)).getContent());
        return "purchases/index";
    }

    @PostMapping("/purchases")
    public String save(@Valid @ModelAttribute PurchaseForm form, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos de la compra.");
            return "redirect:/purchases";
        }
        Purchase purchase = purchaseService.register(form);
        redirectAttributes.addFlashAttribute("success", "Compra registrada. Inventario actualizado.");
        return "redirect:/purchases";
    }
}
