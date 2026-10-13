package com.tienda.pos.supplier;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class SupplierService {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
        this.repository = repository;
    }

    @Transactional
    public Supplier save(Supplier input) {
        Supplier supplier = input.getId() == null ? new Supplier() : repository.findByIdAndTenantId(input.getId(), currentTenant.id())
                .orElseThrow(() -> new DomainException("Proveedor no encontrado."));
        supplier.setTenant(currentTenant.get());
        supplier.setName(trimToNull(input.getName()));
        supplier.setCompanyName(trimToNull(input.getCompanyName()));
        supplier.setPhone(trimToNull(input.getPhone()));
        supplier.setEmail(trimToNull(input.getEmail()));
        supplier.setAddress(trimToNull(input.getAddress()));
        supplier.setTaxId(trimToNull(input.getTaxId()));
        supplier.setNotes(trimToNull(input.getNotes()));
        supplier.setActive(input.isActive());
        return repository.save(supplier);
    }

    @Transactional
    public Supplier setActive(Long id, boolean active) {
        Supplier supplier = repository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new DomainException("Proveedor no encontrado."));
        supplier.setActive(active);
        return repository.save(supplier);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
