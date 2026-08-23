package com.tienda.pos.expense;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "expense_category")
public class ExpenseCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
