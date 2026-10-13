package com.tienda.pos.credit;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreditChargeForm {
    @NotNull @DecimalMin("0.01")
    private BigDecimal amount;
    @NotBlank
    private String concept;
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
}
