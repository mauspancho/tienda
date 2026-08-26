package com.tienda.pos.expense;

import com.tienda.pos.common.NormalMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ExpenseController {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;

    public ExpenseController(ExpenseRepository expenseRepository, ExpenseCategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/expenses")
    public String list(Model model) {
        model.addAttribute("expenses", expenseRepository.findAllByOrderByExpenseDateDesc(PageRequest.of(0, 50)));
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByNameAsc());
        return "expenses/index";
    }

    @PostMapping("/expenses")
    public String save(@RequestParam String concept, @RequestParam Long categoryId, @RequestParam BigDecimal amount,
                       @RequestParam LocalDate expenseDate, @RequestParam(required = false) String notes,
                       RedirectAttributes redirectAttributes) {
        Expense expense = new Expense();
        expense.setConcept(concept);
        expense.setCategory(categoryRepository.findById(categoryId).orElse(null));
        expense.setAmount(amount);
        expense.setExpenseDate(expenseDate);
        expense.setNotes(notes);
        expenseRepository.save(expense);
        redirectAttributes.addFlashAttribute("success", "Gasto registrado.");
        return "redirect:/admin/expenses";
    }
}
