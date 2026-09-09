package com.tienda.pos.inventory;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class InventoryController {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CurrentTenant currentTenant;

    public InventoryController(InventoryMovementRepository movementRepository, ProductRepository productRepository,
                               InventoryService inventoryService, CurrentTenant currentTenant) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/inventory")
    public String list(@RequestParam(required = false) Long productId, Model model) {
        Long tenantId = currentTenant.id();
        model.addAttribute("movements", productId == null
                ? movementRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 50))
                : movementRepository.findByTenantIdAndProductIdOrderByCreatedAtDesc(tenantId, productId, PageRequest.of(0, 50)));
        model.addAttribute("adjustmentForm", new InventoryAdjustmentForm());
        model.addAttribute("products", productRepository.findByTenantIdOrderByNameAsc(tenantId, PageRequest.of(0, 500)).getContent());
        model.addAttribute("movementTypes", InventoryMovementType.values());
        return "inventory/index";
    }

    @PostMapping("/inventory/adjust")
    public String adjust(@Valid @ModelAttribute InventoryAdjustmentForm form, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del ajuste.");
            return "redirect:/admin/inventory";
        }
        inventoryService.adjust(form);
        redirectAttributes.addFlashAttribute("success", "Inventario actualizado.");
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/movements/{id}/reverse")
    public String reverseMovement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            inventoryService.reverseMovement(id);
            redirectAttributes.addFlashAttribute("success", "Movimiento retirado y stock ajustado.");
        } catch (DomainException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/inventory";
    }
}