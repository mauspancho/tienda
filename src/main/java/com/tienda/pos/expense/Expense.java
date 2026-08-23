package com.tienda.pos.expense;

import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense")
public class Expense extends BaseEntity {

    @Column(nullable = false)
    private String concept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate expenseDate = LocalDate.now();

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_session_id")
    private CashRegisterSession cashRegisterSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public CashRegisterSession getCashRegisterSession() { return cashRegisterSession; }
    public void setCashRegisterSession(CashRegisterSession cashRegisterSession) { this.cashRegisterSession = cashRegisterSession; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
}
