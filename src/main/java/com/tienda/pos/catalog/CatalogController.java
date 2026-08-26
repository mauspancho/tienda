package com.tienda.pos.catalog;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.settings.BusinessSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@NormalMode
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "") String q,
                        @RequestParam(required = false) Long category,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        BusinessSettings settings = catalogService.settings();
        model.addAttribute("settings", settings);
        model.addAttribute("catalogTitle", catalogService.title(settings));
        model.addAttribute("catalogSubtitle", catalogService.subtitle(settings));
        model.addAttribute("categories", catalogService.activeCategories());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("products", settings.isCatalogEnabled() ? catalogService.search(q, category, page) : org.springframework.data.domain.Page.empty());
        model.addAttribute("promotions", settings.isCatalogEnabled() ? catalogService.promotions() : java.util.List.of());
        return "catalog/index";
    }

    @GetMapping("/producto/{id}")
    public String product(@PathVariable Long id, Model model) {
        BusinessSettings settings = catalogService.settings();
        if (!settings.isCatalogEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        CatalogProductView product = catalogService.detail(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("settings", settings);
        model.addAttribute("catalogTitle", catalogService.title(settings));
        model.addAttribute("catalogSubtitle", catalogService.subtitle(settings));
        model.addAttribute("product", product);
        return "catalog/product";
    }
}
