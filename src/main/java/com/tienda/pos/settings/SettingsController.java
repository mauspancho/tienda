package com.tienda.pos.settings;

import com.tienda.pos.catalog.CatalogImageService;
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

    private final BusinessSettingsRepository settingsRepository;
    private final CatalogImageService catalogImageService;

    public SettingsController(BusinessSettingsRepository settingsRepository, CatalogImageService catalogImageService) {
        this.settingsRepository = settingsRepository;
        this.catalogImageService = catalogImageService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settingsRepository.findById(1L).orElseGet(BusinessSettings::new));
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
        BusinessSettings current = settingsRepository.findById(1L).orElseGet(BusinessSettings::new);
        String previousLogo = current.getLogoPath();
        String newLogo = null;
        try {
            current.setId(1L);
            current.setStoreName(settings.getStoreName());
            current.setAddress(settings.getAddress());
            current.setPhone(settings.getPhone());
            current.setTaxId(settings.getTaxId());
            current.setCurrency(settings.getCurrency());
            current.setCurrencySymbol(settings.getCurrencySymbol());
            current.setTimezone(settings.getTimezone());
            current.setDefaultTax(settings.getDefaultTax());
            current.setCatalogEnabled(settings.isCatalogEnabled());
            current.setCatalogTitle(blankToNull(settings.getCatalogTitle()));
            current.setCatalogSubtitle(blankToNull(settings.getCatalogSubtitle()));
            current.setPromotionTitle(blankToNull(settings.getPromotionTitle()));
            current.setNegativeStockAllowed(settings.isNegativeStockAllowed());
            if (removeLogo) {
                current.setLogoPath(null);
            }
            if (logoFile != null && !logoFile.isEmpty()) {
                newLogo = catalogImageService.store(logoFile);
                current.setLogoPath(newLogo);
            }
            settingsRepository.save(current);
            if ((removeLogo || newLogo != null) && catalogImageService.isLocalLogo(previousLogo)) {
                catalogImageService.deleteLocalLogo(previousLogo);
            }
            redirectAttributes.addFlashAttribute("success", "Configuración guardada.");
        } catch (DomainException ex) {
            if (newLogo != null) {
                catalogImageService.deleteLocalLogo(newLogo);
            }
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
