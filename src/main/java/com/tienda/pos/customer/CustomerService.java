package com.tienda.pos.customer;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@NormalMode
public class CustomerService {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;
    private final CustomerRepository repository;
    public CustomerService(CustomerRepository repository, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.currentTenant = currentTenant; this.repository = repository; }

    public List<Customer> search(String query) {
        return repository.search(currentTenant.id(), query == null ? "" : query.trim());
    }

    public Customer get(Long id) {
        return repository.findByIdAndTenantId(id, currentTenant.id()).orElseThrow(() -> new DomainException("Cliente no encontrado."));
    }

    @Transactional
    public Customer create(CustomerForm form) {
        Customer customer = new Customer();
        customer.setTenant(currentTenant.get());
        apply(customer, form);
        customer.setCreatedBy(CurrentUser.username());
        return repository.save(customer);
    }

    @Transactional
    public Customer update(Long id, CustomerForm form) {
        Customer customer = get(id);
        if ("Público General".equalsIgnoreCase(customer.getName()) && !form.isActive()) {
            throw new DomainException("Público General debe permanecer activo.");
        }
        apply(customer, form);
        customer.setUpdatedBy(CurrentUser.username());
        return repository.save(customer);
    }

    private void apply(Customer customer, CustomerForm form) {
        String name = clean(form.getName());
        if (name == null) throw new DomainException("El nombre es obligatorio.");
        customer.setName(name);
        customer.setLastName(clean(form.getLastName()));
        customer.setPhone(clean(form.getPhone()));
        customer.setNotes(clean(form.getNotes()));
        customer.setActive(form.isActive());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
