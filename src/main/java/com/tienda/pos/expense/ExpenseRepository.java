package com.tienda.pos.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findByTenantIdOrderByExpenseDateDesc(Long tenantId, Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.tenant.id = :tenantId and e.expenseDate between :start and :end")
    BigDecimal totalBetween(@Param("tenantId") Long tenantId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select e.expenseDate, coalesce(sum(e.amount), 0)
            from Expense e
            where e.tenant.id = :tenantId and e.expenseDate between :start and :end
            group by e.expenseDate
            order by e.expenseDate
            """)
    List<Object[]> dailyTotalsBetween(@Param("tenantId") Long tenantId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}