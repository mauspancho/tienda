package com.tienda.pos.product;

import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.whatsapp.ManualWhatsappPromotionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Set;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final BarcodeLabelService barcodeLabelService;
    private final CurrentTenant currentTenant;
    private final ManualWhatsappPromotionService manualWhatsappPromotionService;
    private final ProductBrandService productBrandService;
    private final ProductSharingService productSharingService;

    public ProductController(ProductRepository productRepository, ProductService productService,
                             CategoryRepository categoryRepository, SupplierRepository supplierRepository,
                             BarcodeLabelService barcodeLabelService, CurrentTenant currentTenant,
                             ManualWhatsappPromotionService manualWhatsappPromotionService,
                             ProductBrandService productBrandService,
                             ProductSharingService productSharingService) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.barcodeLabelService = barcodeLabelService;
        this.currentTenant = currentTenant;
        this.manualWhatsappPromotionService = manualWhatsappPromotionService;
        this.productBrandService = productBrandService;
        this.productSharingService = productSharingService;
    }

    @GetMapping("/products")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String name,
                       @RequestParam(defaultValue = "") String brand,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) BigDecimal minPrice,
                       @RequestParam(required = false) BigDecimal maxPrice,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) Boolean whatsapp,
                       @RequestParam(defaultValue = "name") String sort,
                       @RequestParam(defaultValue = "asc") String direction,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Long tenantId = currentTenant.id();
        String query = normalize(q);
        String productName = normalize(name);
        String productBrand = normalize(brand);
        BigDecimal normalizedMinPrice = nonNegative(minPrice);
        BigDecimal normalizedMaxPrice = nonNegative(maxPrice);
        if (normalizedMinPrice != null && normalizedMaxPrice != null
                && normalizedMinPrice.compareTo(normalizedMaxPrice) > 0) {
            BigDecimal previousMin = normalizedMinPrice;
            normalizedMinPrice = normalizedMaxPrice;
            normalizedMaxPrice = previousMin;
        }
        String sortProperty = productSortProperty(sort);
        String sortDirection = "desc".equalsIgnoreCase(direction) ? "desc" : "asc";
        int currentPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(currentPage, 20, productSort(sortProperty, sortDirection));
        Page<Product> products = productPage(tenantId, query, productName, productBrand, categoryId,
                normalizedMinPrice, normalizedMaxPrice, active, whatsapp, pageable);
        if (currentPage > Math.max(products.getTotalPages() - 1, 0)) {
            currentPage = Math.max(products.getTotalPages() - 1, 0);
            pageable = PageRequest.of(currentPage, 20, productSort(sortProperty, sortDirection));
            products = productPage(tenantId, query, productName, productBrand, categoryId,
                    normalizedMinPrice, normalizedMaxPrice, active, whatsapp, pageable);
        }
        model.addAttribute("products", products);
        model.addAttribute("q", query);
        model.addAttribute("name", productName);
        model.addAttribute("brand", productBrand);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("minPrice", normalizedMinPrice);
        model.addAttribute("maxPrice", normalizedMaxPrice);
        model.addAttribute("active", active);
        model.addAttribute("whatsapp", whatsapp);
        model.addAttribute("sort", sortProperty);
        model.addAttribute("direction", sortDirection);
        model.addAttribute("categories", categoryRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId));
        return "products/index";
    }

    private Page<Product> productPage(Long tenantId, String query, String name, String brand, Long categoryId,
                                      BigDecimal minPrice, BigDecimal maxPrice, Boolean active,
                                      Boolean whatsapp, Pageable pageable) {
        return productRepository.filter(tenantId, query, name, brand, categoryId, minPrice, maxPrice, active, whatsapp, pageable);
    }

    private Sort productSort(String property, String direction) {
        Sort.Direction sortDirection = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String productSortProperty(String sort) {
        String property = normalize(sort);
        return Set.of("name", "brand", "code", "salePrice", "purchaseCost", "currentStock").contains(property)
                ? property
                : "name";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? null : value.max(BigDecimal.ZERO);
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
        Product product = productRepository.findDetailedByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        prepareForm(model, ProductForm.from(product));
        model.addAttribute("initialBarcode", "");
        model.addAttribute("autoLookup", false);
        return "products/form";
    }

    @GetMapping("/products/brands")
    public String brands(Model model) {
        model.addAttribute("report", productBrandService.report(currentTenant.id()));
        return "products/brands";
    }

    @PostMapping("/products/brands/normalize")
    public String normalizeBrands(RedirectAttributes redirectAttributes) {
        ProductBrandService.BrandNormalizationResult result = productBrandService.normalizeExisting(currentTenant.id());
        redirectAttributes.addFlashAttribute("success", result.changedProducts() == 0
                ? "Las marcas ya estaban normalizadas."
                : "Se normalizaron " + result.changedProducts() + " productos correctamente.");
        return "redirect:/admin/products/brands";
    }

    @PostMapping("/products/{id}/manual-whatsapp-promotion")
    @ResponseBody
    public ResponseEntity<?> sendManualWhatsappPromotion(@PathVariable Long id,
                                                          @RequestParam String message,
                                                          @RequestParam String requestKey,
                                                          @RequestParam(defaultValue = "false") boolean sendToNumber,
                                                          @RequestParam(required = false) String phone) {
        try {
            return ResponseEntity.ok(manualWhatsappPromotionService.send(id, message, requestKey,
                    sendToNumber, phone));
        } catch (DomainException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/products/{id}/share-preview")
    @ResponseBody
    public ResponseEntity<?> sharePreview(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productSharingService.preview(id));
        } catch (DomainException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/products/{id}/share-image")
    public ResponseEntity<byte[]> shareImage(@PathVariable Long id) {
        try {
            ProductImageService.ProductImagePayload image = productSharingService.image(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(image.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + image.filename().replace("\"", "") + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(image.bytes());
        } catch (DomainException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/products/{id}/barcode-label")
    public String barcodeLabel(@PathVariable Long id, @RequestParam(defaultValue = "1") int quantity, Model model) {
        Product product = productRepository.findDetailedByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        int labelCount = Math.max(1, Math.min(quantity, 500));
        String barcode = product.getBarcode();
        model.addAttribute("product", product);
        model.addAttribute("barcode", barcode);
        model.addAttribute("quantity", labelCount);
        model.addAttribute("labels", java.util.stream.IntStream.range(0, labelCount).boxed().toList());
        model.addAttribute("barcodeSvg", barcodeLabelService.code128Svg(barcode));
        return "products/barcode-label";
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
                       @RequestParam(required = false) MultipartFile imageFile,
                       Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, form);
            model.addAttribute("initialBarcode", "");
            model.addAttribute("autoLookup", false);
            return "products/form";
        }
        try {
            productService.save(form, imageFile);
        } catch (DomainException ex) {
            bindingResult.reject("product.image", ex.getMessage());
            prepareForm(model, form);
            model.addAttribute("initialBarcode", "");
            model.addAttribute("autoLookup", false);
            return "products/form";
        }
        redirectAttributes.addFlashAttribute("success", "Producto guardado correctamente.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/promote")
    public String promote(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.promote(id);
            redirectAttributes.addFlashAttribute("success", "Producto promocionado.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/unpromote")
    public String unpromote(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.removePromotion(id);
        redirectAttributes.addFlashAttribute("success", "Promoción retirada.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/whatsapp-promotion")
    public String updateWhatsappPromotion(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean selected,
                                          @RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "") String name,
                                          @RequestParam(defaultValue = "") String brand,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) BigDecimal minPrice,
                                          @RequestParam(required = false) BigDecimal maxPrice,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(required = false) Boolean whatsapp,
                                          @RequestParam(defaultValue = "name") String sort,
                                          @RequestParam(defaultValue = "asc") String direction,
                                          @RequestParam(defaultValue = "0") int page,
                                          RedirectAttributes redirectAttributes) {
        try {
            productService.setWhatsappPromotion(id, selected);
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        redirectAttributes.addAttribute("q", normalize(q));
        redirectAttributes.addAttribute("name", normalize(name));
        redirectAttributes.addAttribute("brand", normalize(brand));
        if (categoryId != null) redirectAttributes.addAttribute("categoryId", categoryId);
        if (minPrice != null) redirectAttributes.addAttribute("minPrice", minPrice);
        if (maxPrice != null) redirectAttributes.addAttribute("maxPrice", maxPrice);
        if (active != null) redirectAttributes.addAttribute("active", active);
        if (whatsapp != null) redirectAttributes.addAttribute("whatsapp", whatsapp);
        redirectAttributes.addAttribute("sort", productSortProperty(sort));
        redirectAttributes.addAttribute("direction", "desc".equalsIgnoreCase(direction) ? "desc" : "asc");
        redirectAttributes.addAttribute("page", Math.max(page, 0));
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/whatsapp-promotion/state")
    @ResponseBody
    public ResponseEntity<Void> updateWhatsappPromotionState(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "false") boolean selected) {
        try {
            productService.setWhatsappPromotion(id, selected);
            return ResponseEntity.noContent().build();
        } catch (DomainException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    private void prepareForm(Model model, ProductForm form) {
        Long tenantId = currentTenant.id();
        model.addAttribute("productForm", form);
        model.addAttribute("categories", categoryRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId));
        model.addAttribute("suppliers", supplierRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId));
        model.addAttribute("units", UnitType.values());
    }

    public record GeneratedCode(String value) {
    }
}
