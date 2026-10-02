package com.tienda.pos.api.v1.expense;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.expense.Expense;
import com.tienda.pos.expense.ExpenseCategory;
import com.tienda.pos.expense.ExpenseCategoryRepository;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.expense.ExpenseService;
import com.tienda.pos.user.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasRole('ADMIN')")
public class ApiExpenseController {

    private final ExpenseRepository repository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseService service;

    public ApiExpenseController(ExpenseRepository repository, ExpenseCategoryRepository categoryRepository,
                                ExpenseService service) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.service = service;
    }

    @GetMapping
    public ApiPageResponse<ExpenseResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<Expense> expenses = repository.findAllByOrderByExpenseDateDesc(
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100))));
        return ApiPageResponse.from(expenses, ExpenseResponse::from);
    }

    @GetMapping("/{id}")
    public ExpenseResponse detail(@PathVariable Long id) {
        return ExpenseResponse.from(repository.findDetailedById(id)
                .orElseThrow(() -> new ApiNotFoundException("Gasto no encontrado.")));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        Expense expense = service.create(request.concept(), request.categoryId(), request.amount(),
                request.expenseDate(), request.notes());
        return ExpenseResponse.from(repository.findDetailedById(expense.getId()).orElse(expense));
    }

    public record ExpenseRequest(@NotBlank String concept, @NotNull Long categoryId,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotNull LocalDate expenseDate, String notes) {
    }

    public record CategoryResponse(Long id, String name) {
        static CategoryResponse from(ExpenseCategory category) {
            return new CategoryResponse(category.getId(), category.getName());
        }
    }

    public record ExpenseResponse(Long id, String concept, CategoryResponse category,
                                  BigDecimal amount, LocalDate expenseDate, String notes,
                                  Long userId, String username) {
        static ExpenseResponse from(Expense expense) {
            CategoryResponse category = expense.getCategory() == null ? null : CategoryResponse.from(expense.getCategory());
            AppUser user = expense.getUser();
            return new ExpenseResponse(expense.getId(), expense.getConcept(), category, expense.getAmount(),
                    expense.getExpenseDate(), expense.getNotes(), user == null ? null : user.getId(),
                    user == null ? null : user.getUsername());
        }
    }
}
