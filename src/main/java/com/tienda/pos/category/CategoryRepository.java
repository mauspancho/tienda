package com.tienda.pos.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Page<Category> findByTenantIdOrderByNameAsc(Long tenantId, Pageable pageable);
    Page<Category> findByTenantIdAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    List<Category> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
    Optional<Category> findByIdAndTenantId(Long id, Long tenantId);
}