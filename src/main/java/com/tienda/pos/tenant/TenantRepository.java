package com.tienda.pos.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    List<Tenant> findTop2ByActiveTrueOrderByIdAsc();
    Optional<Tenant> findByCode(String code);
}
