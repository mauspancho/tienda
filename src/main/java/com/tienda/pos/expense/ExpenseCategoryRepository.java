package com.tienda.pos.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
    Optional<ExpenseCategory> findByIdAndTenantId(Long id, Long tenantId);
}