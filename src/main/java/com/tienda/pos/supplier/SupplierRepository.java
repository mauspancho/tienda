package com.tienda.pos.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Page<Supplier> findByTenantIdOrderByNameAsc(Long tenantId, Pageable pageable);
    Page<Supplier> findByTenantIdAndNameContainingIgnoreCaseOrTenantIdAndCompanyNameContainingIgnoreCase(
            Long nameTenantId, String name, Long companyTenantId, String companyName, Pageable pageable);
    List<Supplier> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);
}