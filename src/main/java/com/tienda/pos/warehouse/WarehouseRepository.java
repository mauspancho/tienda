package com.tienda.pos.warehouse;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @EntityGraph(attributePaths = {"tenant", "branch", "branch.business"})
    Optional<Warehouse> findFirstByTenantIdAndActiveTrueAndMainWarehouseTrueOrderByIdAsc(Long tenantId);

    @EntityGraph(attributePaths = {"tenant", "branch", "branch.business"})
    Optional<Warehouse> findFirstByTenantIdAndActiveTrueOrderByIdAsc(Long tenantId);
}