package com.tienda.pos.supplier;

import com.tienda.pos.common.NormalMode;
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

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping("/suppliers")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("suppliers", q.isBlank()
                ? supplierRepository.findAll(PageRequest.of(0, 50))
                : supplierRepository.findByNameContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(q, q, PageRequest.of(0, 50)));
        model.addAttribute("supplier", new Supplier());
        model.addAttribute("q", q);
        return "suppliers/index";
    }

    @PostMapping("/suppliers")
    public String save(@Valid @ModelAttribute Supplier supplier, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del proveedor.");
            return "redirect:/admin/suppliers";
        }
        supplierRepository.save(supplier);
        redirectAttributes.addFlashAttribute("success", "Proveedor guardado correctamente.");
        return "redirect:/admin/suppliers";
    }
}
