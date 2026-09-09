package com.tienda.pos.branch;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    @EntityGraph(attributePaths = "business")
    Optional<Branch> findFirstByActiveTrueOrderByIdAsc();
}
