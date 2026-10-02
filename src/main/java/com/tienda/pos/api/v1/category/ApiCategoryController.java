package com.tienda.pos.api.v1.category;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.category.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@PreAuthorize("hasRole('ADMIN')")
public class ApiCategoryController {

    private final CategoryRepository repository;
    private final CategoryService service;

    public ApiCategoryController(CategoryRepository repository, CategoryService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public ApiPageResponse<CategoryResponse> list(@RequestParam(defaultValue = "") String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                Sort.by("name").ascending());
        Page<Category> result = q == null || q.isBlank() ? repository.findAll(pageable)
                : repository.findByNameContainingIgnoreCase(q.trim(), pageable);
        return ApiPageResponse.from(result, CategoryResponse::from);
    }

    @GetMapping("/{id}")
    public CategoryResponse detail(@PathVariable Long id) {
        return CategoryResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(service.save(request.toEntity(null)));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        find(id);
        return CategoryResponse.from(service.save(request.toEntity(id)));
    }

    @PatchMapping("/{id}/active")
    public CategoryResponse active(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return CategoryResponse.from(service.setActive(id, request.active()));
    }

    private Category find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiNotFoundException("Categoría no encontrada."));
    }

    public record CategoryRequest(@NotBlank String name, String description, @NotNull Boolean active) {
        Category toEntity(Long id) {
            Category category = new Category();
            category.setId(id);
            category.setName(name);
            category.setDescription(description);
            category.setActive(active);
            return category;
        }
    }

    public record ActiveRequest(boolean active) {
    }

    public record CategoryResponse(Long id, String name, String description, boolean active) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive());
        }
    }
}
