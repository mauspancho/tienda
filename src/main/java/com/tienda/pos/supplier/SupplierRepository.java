package com.tienda.pos.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Page<Supplier> findByNameContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(String name, String companyName, Pageable pageable);
    List<Supplier> findByActiveTrueOrderByNameAsc();
}
