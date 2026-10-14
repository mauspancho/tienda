package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@RequestMapping("/platform/integrations/wppconnect")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformWhatsappController {
    private final WhatsappPlatformService service;
    private final TenantRepository tenants;
    public PlatformWhatsappController(WhatsappPlatformService service, TenantRepository tenants) { this.service = service; this.tenants = tenants; }

    @GetMapping
    public String page(Model model) {
        var data = service.page();
        if (!model.containsAttribute("form")) {
            WhatsappProviderForm form = new WhatsappProviderForm();
            if (!data.providers().isEmpty()) {
                WhatsappProvider current = data.providers().getFirst().provider();
                form.setId(current.getId()); form.setName(current.getName()); form.setBaseUrl(current.getBaseUrl()); form.setEnabled(current.isEnabled());
            }
            model.addAttribute("form", form);
        }
        model.addAttribute("data", data);
        model.addAttribute("tenants", tenants.findAll(Sort.by("name")));
        return "platform/wppconnect";
    }

    @PostMapping("/provider")
    public String save(@Valid @ModelAttribute("form") WhatsappProviderForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Revisa los datos del proveedor.");
            return "redirect:/platform/integrations/wppconnect";
        }
        try { service.save(form); flash.addFlashAttribute("success", "Proveedor WPPConnect guardado."); }
        catch (DomainException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/platform/integrations/wppconnect";
    }

    @PostMapping("/provider/{id}/test")
    public String test(@PathVariable Long id, RedirectAttributes flash) {
        ProviderStatus status = service.test(id);
        flash.addFlashAttribute(status == ProviderStatus.AVAILABLE ? "success" : "error", "Resultado de conexión: " + status.name());
        return "redirect:/platform/integrations/wppconnect";
    }

    @PostMapping("/associate")
    public String associate(@RequestParam Long providerId, @RequestParam Long tenantId, @RequestParam String sessionName,
                            @RequestParam(defaultValue = "false") boolean confirmed, RedirectAttributes flash) {
        try { service.associate(providerId, tenantId, sessionName, confirmed); flash.addFlashAttribute("success", "Sesión existente asociada sin cerrarla ni modificarla."); }
        catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/platform/integrations/wppconnect";
    }
}
