package com.tienda.pos.settings;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settingsService.current());
        return "settings/index";
    }

    @PostMapping("/settings")
    public String save(@Valid @ModelAttribute("settings") BusinessSettings settings, BindingResult bindingResult,
                       @RequestParam(required = false) MultipartFile logoFile,
                       @RequestParam(defaultValue = "false") boolean removeLogo,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa la configuración.");
            return "redirect:/admin/settings";
        }
        try {
            settingsService.save(settings, logoFile, removeLogo);
            redirectAttributes.addFlashAttribute("success", "Configuración guardada.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }

}
