package com.tienda.pos.cash;

import com.tienda.pos.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, Long> {
    Optional<CashRegisterSession> findByCashierAndOpenTrue(AppUser cashier);

    @EntityGraph(attributePaths = {"cashier"})
    @Query("select s from CashRegisterSession s where s.cashier = :cashier and s.open = true")
    Optional<CashRegisterSession> findDetailedByCashierAndOpenTrue(@Param("cashier") AppUser cashier);

    @EntityGraph(attributePaths = {"cashier"})
    Page<CashRegisterSession> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"cashier"})
    @Query("select s from CashRegisterSession s where s.id = :id")
    Optional<CashRegisterSession> findDetailedById(@Param("id") Long id);
}
