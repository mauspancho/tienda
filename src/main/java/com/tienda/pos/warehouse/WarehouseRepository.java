package com.tienda.pos.warehouse;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @EntityGraph(attributePaths = {"branch", "branch.business"})
    Optional<Warehouse> findFirstByActiveTrueAndMainWarehouseTrueOrderByIdAsc();

    @EntityGraph(attributePaths = {"branch", "branch.business"})
    Optional<Warehouse> findFirstByActiveTrueOrderByIdAsc();
}
