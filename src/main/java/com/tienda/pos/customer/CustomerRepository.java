package com.tienda.pos.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByTenantIdAndName(Long tenantId, String name);
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);
}