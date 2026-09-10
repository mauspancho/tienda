package com.tienda.pos.supplier;

import com.tienda.pos.common.NormalMode;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class SupplierController {

    @org.springframework.web.bind.annotation.InitBinder("supplier")
    void bindSupplier(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("name", "companyName", "phone", "email", "address", "taxId", "notes", "active");
    }

    private final SupplierRepository supplierRepository;
    private final CurrentTenant currentTenant;

    public SupplierController(SupplierRepository supplierRepository, CurrentTenant currentTenant) {
        this.supplierRepository = supplierRepository;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/suppliers")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        Long tenantId = currentTenant.id();
        model.addAttribute("suppliers", q.isBlank()
                ? supplierRepository.findByTenantIdOrderByNameAsc(tenantId, PageRequest.of(0, 50))
                : supplierRepository.findByTenantIdAndNameContainingIgnoreCaseOrTenantIdAndCompanyNameContainingIgnoreCase(tenantId, q, tenantId, q, PageRequest.of(0, 50)));
        model.addAttribute("supplier", new Supplier());
        model.addAttribute("q", q);
        return "suppliers/index";
    }

    @PostMapping("/suppliers")
    public String save(@Valid @ModelAttribute Supplier supplier, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.getSuppressedFields().length > 0) {
            throw new org.springframework.security.access.AccessDeniedException("Campos no permitidos.");
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del proveedor.");
            return "redirect:/admin/suppliers";
        }
        supplier.setTenant(currentTenant.get());
        supplierRepository.save(supplier);
        redirectAttributes.addFlashAttribute("success", "Proveedor guardado correctamente.");
        return "redirect:/admin/suppliers";
    }
}
