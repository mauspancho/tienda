package com.tienda.pos.purchase;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.tenant.CurrentTenant;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseController {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PurchaseService purchaseService;
    private final CurrentTenant currentTenant;

    public PurchaseController(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                              ProductRepository productRepository, PurchaseService purchaseService,
                              CurrentTenant currentTenant) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.purchaseService = purchaseService;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/purchases")
    public String list(Model model) {
        Long tenantId = currentTenant.id();
        model.addAttribute("purchases", purchaseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 50)));
        model.addAttribute("purchaseForm", new PurchaseForm());
        model.addAttribute("suppliers", supplierRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId));
        model.addAttribute("products", productRepository.findByTenantIdOrderByNameAsc(tenantId, PageRequest.of(0, 500)).getContent());
        model.addAttribute("fundingSources", PurchaseFundingSource.values());
        return "purchases/index";
    }

    @PostMapping("/purchases")
    public String register(@Valid @ModelAttribute PurchaseForm form, BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos de la compra.");
            return "redirect:/admin/purchases";
        }
        try {
            purchaseService.register(form);
            redirectAttributes.addFlashAttribute("success", "Compra registrada y stock actualizado.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/purchases";
    }
}