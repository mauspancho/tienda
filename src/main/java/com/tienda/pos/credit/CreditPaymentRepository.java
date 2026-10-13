package com.tienda.pos.credit;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CreditPaymentRepository extends JpaRepository<CreditPayment, Long> {
    @EntityGraph(attributePaths = {"customer", "user", "applications", "applications.sale", "applications.charge"})
    List<CreditPayment> findByCustomerIdOrderByCreatedAtAsc(Long customerId);
    @EntityGraph(attributePaths = {"customer", "user", "applications", "applications.sale", "applications.charge"})
    @Query("select p from CreditPayment p where p.id = :id")
    Optional<CreditPayment> findDetailedById(@Param("id") Long id);
    @Query("select coalesce(sum(p.amount), 0) from CreditPayment p where p.status = 'ACTIVE'")
    BigDecimal totalActivePayments();
    @Query("select coalesce(sum(p.amount), 0) from CreditPayment p where p.status = 'ACTIVE' and p.createdAt between :start and :end")
    BigDecimal totalActivePaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "user", "applications", "applications.sale", "applications.charge"})
    @Query("select p from CreditPayment p where p.id = :id")
    Optional<CreditPayment> findByIdForUpdate(@Param("id") Long id);
}
