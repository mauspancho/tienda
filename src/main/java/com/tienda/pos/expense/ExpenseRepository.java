package com.tienda.pos.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findAllByOrderByExpenseDateDesc(Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate between :start and :end")
    BigDecimal totalBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
