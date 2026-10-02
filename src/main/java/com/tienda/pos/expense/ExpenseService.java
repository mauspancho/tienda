package com.tienda.pos.expense;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@NormalMode
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseCategoryRepository categoryRepository,
                          AppUserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Expense create(String concept, Long categoryId, BigDecimal amount, LocalDate expenseDate, String notes) {
        if (concept == null || concept.isBlank()) {
            throw new DomainException("El concepto es obligatorio.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("El importe debe ser mayor a cero.");
        }
        Expense expense = new Expense();
        expense.setConcept(concept.trim());
        expense.setCategory(categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException("Categoría de gasto no encontrada.")));
        expense.setAmount(MoneyUtils.money(amount));
        expense.setExpenseDate(expenseDate == null ? LocalDate.now() : expenseDate);
        expense.setNotes(notes);
        userRepository.findByUsername(CurrentUser.username()).ifPresent(expense::setUser);
        return expenseRepository.save(expense);
    }
}
