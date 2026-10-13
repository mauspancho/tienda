package com.tienda.pos.cash;

import com.tienda.pos.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, Long> {
    @EntityGraph(attributePaths = {"cashier"})
    Optional<CashRegisterSession> findByTenantIdAndCashierAndOpenTrue(Long tenantId, AppUser cashier);

    @EntityGraph(attributePaths = {"cashier"})
    Optional<CashRegisterSession> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"cashier"})
    Page<CashRegisterSession> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"cashier"})
    Page<CashRegisterSession> findByTenantIdAndCashierUsernameOrderByCreatedAtDesc(Long tenantId, String username, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from CashRegisterSession s where s.id = :id and s.tenant.id = :tenantId")
    Optional<CashRegisterSession> lockByIdAndTenantId(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId);

}
