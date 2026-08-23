package com.tienda.pos.settings;

import com.tienda.pos.common.NormalMode;
import jakarta.validation.Valid;
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
public class SettingsController {

    private final BusinessSettingsRepository settingsRepository;

    public SettingsController(BusinessSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settingsRepository.findById(1L).orElseGet(BusinessSettings::new));
        return "settings/index";
    }

    @PostMapping("/settings")
    public String save(@Valid @ModelAttribute("settings") BusinessSettings settings, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa la configuración.");
            return "redirect:/settings";
        }
        BusinessSettings current = settingsRepository.findById(1L).orElseGet(BusinessSettings::new);
        current.setId(1L);
        current.setStoreName(settings.getStoreName());
        current.setAddress(settings.getAddress());
        current.setPhone(settings.getPhone());
        current.setTaxId(settings.getTaxId());
        current.setCurrency(settings.getCurrency());
        current.setCurrencySymbol(settings.getCurrencySymbol());
        current.setTimezone(settings.getTimezone());
        current.setDefaultTax(settings.getDefaultTax());
        current.setLogoPath(settings.getLogoPath());
        current.setNegativeStockAllowed(settings.isNegativeStockAllowed());
        settingsRepository.save(current);
        redirectAttributes.addFlashAttribute("success", "Configuración guardada.");
        return "redirect:/settings";
    }
}
