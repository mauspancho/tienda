package com.tienda.pos.platform;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@RequestMapping("/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantController {
    private final PlatformTenantService tenants;
    private final TenantProvisioningService provisioning;

    public PlatformTenantController(PlatformTenantService tenants, TenantProvisioningService provisioning) {
        this.tenants = tenants;
        this.provisioning = provisioning;
    }

    @GetMapping({"", "/"})
    public String index() { return "redirect:/platform/tenants"; }

    @GetMapping("/tenants")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("tenants", tenants.search(q, page));
        model.addAttribute("q", q);
        return "platform/tenants";
    }

    @GetMapping("/tenants/new")
    public String createForm(Model model) {
        model.addAttribute("form", new TenantCreateForm());
        model.addAttribute("creating", true);
        return "platform/form";
    }

    @PostMapping("/tenants")
    public String create(@Valid @ModelAttribute("form") TenantCreateForm form, BindingResult errors,
                         Model model, RedirectAttributes flash, HttpServletResponse response) {
        if (!errors.hasErrors()) {
            try {
                Long id = provisioning.create(form);
                flash.addFlashAttribute("success", "Tienda creada correctamente.");
                return "redirect:/platform/tenants/" + id;
            } catch (DomainException ex) {
                errors.reject("tenant", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                errors.reject("tenant", "El codigo o el usuario ya existe.");
            }
        }
        form.clearPasswords();
        model.addAttribute("creating", true);
        response.setStatus(400);
        return "platform/form";
    }

    @GetMapping("/tenants/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("detail", tenants.detail(id));
        return "platform/detail";
    }

    @GetMapping("/tenants/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var detail = tenants.detail(id);
        model.addAttribute("form", detail.form());
        editModel(model, id, detail.tenant().code());
        return "platform/form";
    }

    @PostMapping("/tenants/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") TenantForm form,
                         BindingResult errors, Model model, RedirectAttributes flash, HttpServletResponse response) {
        if (!errors.hasErrors()) {
            tenants.update(id, form);
            flash.addFlashAttribute("success", "Tienda actualizada.");
            return "redirect:/platform/tenants/" + id;
        }
        editModel(model, id, tenants.detail(id).tenant().code());
        response.setStatus(400);
        return "platform/form";
    }

    @PostMapping("/tenants/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes flash) {
        tenants.toggle(id);
        flash.addFlashAttribute("success", "Estado de la tienda actualizado.");
        return "redirect:/platform/tenants/" + id;
    }

    @PostMapping("/tenants/{id}/admins/{adminId}/reactivate")
    public String reactivate(@PathVariable Long id, @PathVariable Long adminId, RedirectAttributes flash) {
        tenants.reactivateAdmin(id, adminId);
        flash.addFlashAttribute("success", "Administrador reactivado.");
        return "redirect:/platform/tenants/" + id;
    }

    @PostMapping("/tenants/{id}/admins/{adminId}/reset-password")
    public String resetPassword(@PathVariable Long id, @PathVariable Long adminId,
                                @Valid PasswordResetForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Confirma una contrasena de entre 8 y 72 caracteres.");
        } else {
            tenants.resetPassword(id, adminId, form);
            flash.addFlashAttribute("success", "Contrasena actualizada.");
        }
        return "redirect:/platform/tenants/" + id;
    }

    private void editModel(Model model, Long id, String code) {
        model.addAttribute("creating", false);
        model.addAttribute("tenantId", id);
        model.addAttribute("code", code);
    }
}
