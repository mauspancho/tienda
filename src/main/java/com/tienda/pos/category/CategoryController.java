package com.tienda.pos.category;

import com.tienda.pos.common.NormalMode;
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
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/categories")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("categories", q.isBlank()
                ? categoryRepository.findAll(PageRequest.of(0, 50))
                : categoryRepository.findByNameContainingIgnoreCase(q, PageRequest.of(0, 50)));
        model.addAttribute("category", new Category());
        model.addAttribute("q", q);
        return "categories/index";
    }

    @PostMapping("/categories")
    public String save(@Valid @ModelAttribute Category category, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "El nombre de la categoría es obligatorio.");
            return "redirect:/admin/categories";
        }
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Categoría guardada correctamente.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id).orElseThrow();
        category.setActive(!category.isActive());
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Categoría actualizada.");
        return "redirect:/admin/categories";
    }
}
