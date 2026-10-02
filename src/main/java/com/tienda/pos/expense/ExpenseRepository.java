package com.tienda.pos.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @EntityGraph(attributePaths = {"category", "user"})
    Page<Expense> findAllByOrderByExpenseDateDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> findDetailedById(@Param("id") Long id);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate between :start and :end")
    BigDecimal totalBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select e.expenseDate, coalesce(sum(e.amount), 0)
            from Expense e
            where e.expenseDate between :start and :end
            group by e.expenseDate
            order by e.expenseDate
            """)
    List<Object[]> dailyTotalsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
