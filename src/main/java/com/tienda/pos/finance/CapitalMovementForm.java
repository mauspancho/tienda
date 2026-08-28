package com.tienda.pos.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CapitalMovementForm {
    @NotNull
    private LocalDate movementDate = LocalDate.now();
    @NotNull
    private CapitalMovementType type = CapitalMovementType.OWNER_CONTRIBUTION;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount = BigDecimal.ZERO;
    private String description;

    public LocalDate getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDate movementDate) { this.movementDate = movementDate; }
    public CapitalMovementType getType() { return type; }
    public void setType(CapitalMovementType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
