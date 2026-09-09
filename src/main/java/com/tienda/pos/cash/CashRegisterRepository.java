package com.tienda.pos.cash;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {
    @EntityGraph(attributePaths = {"branch", "branch.business"})
    Optional<CashRegister> findFirstByActiveTrueOrderByIdAsc();
}
