package com.tienda.pos.category;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class CategoryService {

    private final CategoryRepository repository;
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;

    public CategoryService(CategoryRepository repository, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public Category save(Category input) {
        Category category = input.getId() == null ? new Category() : repository.findByIdAndTenantId(input.getId(), currentTenant.id())
                .orElseThrow(() -> new DomainException("Categoría no encontrada."));
        category.setTenant(currentTenant.get());
        category.setName(input.getName().trim());
        category.setDescription(input.getDescription());
        category.setActive(input.isActive());
        return repository.save(category);
    }

    @Transactional
    public Category setActive(Long id, boolean active) {
        Category category = repository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new DomainException("Categoría no encontrada."));
        category.setActive(active);
        return repository.save(category);
    }

    @Transactional
    public Category toggle(Long id) {
        Category category = repository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new DomainException("Categoría no encontrada."));
        return setActive(id, !category.isActive());
    }
}
