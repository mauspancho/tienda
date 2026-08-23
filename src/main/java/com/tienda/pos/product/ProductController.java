package com.tienda.pos.product;

import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.supplier.SupplierRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@PreAuthorize("hasRole('ADMIN')")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductController(ProductRepository productRepository, ProductService productService,
                             CategoryRepository categoryRepository, SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping("/products")
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 20, Sort.by("name"));
        model.addAttribute("products", q.isBlank() ? productRepository.findAll(pageable) : productRepository.search(q, pageable));
        model.addAttribute("q", q);
        return "products/index";
    }

    @GetMapping("/products/new")
    public String create(@RequestParam(required = false) String barcode,
                         @RequestParam(defaultValue = "false") boolean lookup,
                         Model model) {
        ProductForm form = new ProductForm();
        String normalizedBarcode = ProductService.normalizeBarcode(barcode);
        if (normalizedBarcode != null) {
            form.setBarcode(normalizedBarcode);
        }
        prepareForm(model, form);
        model.addAttribute("initialBarcode", normalizedBarcode == null ? "" : normalizedBarcode);
        model.addAttribute("autoLookup", lookup && normalizedBarcode != null);
        return "products/form";
    }

    @GetMapping("/products/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Product product = productRepository.findDetailedById(id).orElseThrow();
        prepareForm(model, ProductForm.from(product));
        model.addAttribute("initialBarcode", "");
        model.addAttribute("autoLookup", false);
        return "products/form";
    }

    @GetMapping("/products/generate-code")
    @ResponseBody
    public GeneratedCode generateCode(@RequestParam(defaultValue = "code") String type) {
        String value = "barcode".equalsIgnoreCase(type)
                ? productService.generateUniqueBarcode()
                : productService.generateUniqueProductCode();
        return new GeneratedCode(value);
    }

    @PostMapping("/products")
    public String save(@Valid @ModelAttribute("productForm") ProductForm form, BindingResult bindingResult,
                       Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, form);
            model.addAttribute("initialBarcode", "");
            model.addAttribute("autoLookup", false);
            return "products/form";
        }
        productService.save(form);
        redirectAttributes.addFlashAttribute("success", "Producto guardado correctamente.");
        return "redirect:/products";
    }

    private void prepareForm(Model model, ProductForm form) {
        model.addAttribute("productForm", form);
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("suppliers", supplierRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("units", UnitType.values());
    }

    public record GeneratedCode(String value) {
    }
}
