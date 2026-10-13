package com.tienda.pos.credit;

import com.tienda.pos.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreditPaymentForm {
    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotNull
    private PaymentMethod method = PaymentMethod.CASH;
    private String notes;
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
