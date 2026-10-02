package com.tienda.pos.supplier;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Supplier save(Supplier input) {
        Supplier supplier = input.getId() == null ? new Supplier() : repository.findById(input.getId())
                .orElseThrow(() -> new DomainException("Proveedor no encontrado."));
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
        Supplier supplier = repository.findById(id)
                .orElseThrow(() -> new DomainException("Proveedor no encontrado."));
        supplier.setActive(active);
        return repository.save(supplier);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
