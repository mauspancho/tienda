package com.tienda.pos.setup;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ConditionalOnProperty(name = "tienda.setup-mode", havingValue = "true")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping({"/", "/setup"})
    public String setup(Model model) {
        if (!model.containsAttribute("setupForm")) {
            model.addAttribute("setupForm", new SetupForm());
        }
        return "setup/index";
    }

    @PostMapping("/setup/test")
    public String test(@ModelAttribute SetupForm form, RedirectAttributes redirectAttributes) {
        try {
            setupService.testConnection(form);
            redirectAttributes.addFlashAttribute("success", "Conexión correcta.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No fue posible conectar: " + ex.getMessage());
        }
        redirectAttributes.addFlashAttribute("setupForm", form);
        return "redirect:/setup";
    }

    @PostMapping("/setup/install")
    public String install(@Valid @ModelAttribute SetupForm form, BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los campos requeridos.");
            redirectAttributes.addFlashAttribute("setupForm", form);
            return "redirect:/setup";
        }
        try {
            setupService.install(form);
            redirectAttributes.addFlashAttribute("success",
                    "Instalación completada correctamente. Reinicia la aplicación y entra con tu administrador.");
            return "redirect:/setup/done";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No fue posible completar la instalación: " + ex.getMessage());
            redirectAttributes.addFlashAttribute("setupForm", form);
            return "redirect:/setup";
        } finally {
            form.setAdminPassword(null);
            form.setAdminPasswordConfirm(null);
        }
    }

    @GetMapping("/setup/done")
    public String done() {
        return "setup/done";
    }
}
