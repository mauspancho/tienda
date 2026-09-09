package com.tienda.pos.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findFirstByActiveTrueOrderByIdAsc();
    Optional<Tenant> findByCode(String code);
}