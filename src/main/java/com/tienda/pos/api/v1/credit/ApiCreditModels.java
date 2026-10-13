package com.tienda.pos.api.v1.credit;

import com.tienda.pos.credit.*;
import com.tienda.pos.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiCreditModels {
    private ApiCreditModels() {}
    public record SummaryResponse(BigDecimal pending, long customersWithDebt, BigDecimal totalGranted, BigDecimal totalPaid) {
        static SummaryResponse from(CreditViews.Summary value) { return new SummaryResponse(value.pending(), value.customersWithDebt(), value.totalGranted(), value.totalPaid()); }
    }
    public record CustomerCreditResponse(Long id, String name, String lastName, String fullName, String phone,
                                         boolean active, BigDecimal balance, BigDecimal totalGranted,
                                         BigDecimal totalPaid, LocalDateTime lastActivity) {
        static CustomerCreditResponse from(CreditViews.CustomerRow value) {
            var c = value.customer();
            return new CustomerCreditResponse(c.getId(), c.getName(), c.getLastName(), c.getFullName(), c.getPhone(),
                    c.isActive(), value.balance(), value.totalGranted(), value.totalPaid(), value.lastActivity());
        }
    }
    public record SaleLine(String product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    public record CreditSale(Long id, String folio, LocalDateTime date, BigDecimal total, BigDecimal paid,
                             BigDecimal balance, String status, List<SaleLine> items) {
        static CreditSale from(CreditViews.SaleRow row) {
            return new CreditSale(row.sale().getId(), row.sale().getFolio(), row.sale().getSaleDate(), row.sale().getTotal(),
                    row.paid(), row.balance(), row.sale().getStatus().name(), row.sale().getItems().stream()
                    .map(i -> new SaleLine(i.getProductNameSnapshot(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal())).toList());
        }
    }
    public record PaymentView(Long id, LocalDateTime date, BigDecimal amount, PaymentMethod method,
                              String notes, String status, String username) {
        static PaymentView from(CreditPayment p) { return new PaymentView(p.getId(), p.getCreatedAt(), p.getAmount(), p.getMethod(), p.getNotes(), p.getStatus().name(), p.getUser().getUsername()); }
    }
    public record ChargeView(Long id, LocalDateTime date, BigDecimal amount, String concept, String status,
                             String username, BigDecimal applied, BigDecimal balance) {
        static ChargeView from(CreditViews.ChargeRow row) {
            CreditCharge charge = row.charge();
            return new ChargeView(charge.getId(), charge.getCreatedAt(), charge.getAmount(), charge.getConcept(),
                    charge.getStatus().name(), charge.getUser().getUsername(), row.paid(), row.balance());
        }
    }
    public record MovementView(LocalDateTime date, String type, String reference, String detail,
                               BigDecimal charge, BigDecimal payment, BigDecimal resultingBalance,
                               String username, boolean reversed, Long entityId) {
        static MovementView from(CreditViews.Movement m) { return new MovementView(m.date(), m.type(), m.reference(), m.detail(), m.charge(), m.payment(), m.resultingBalance(), m.username(), m.reversed(), m.entityId()); }
    }
    public record StatementResponse(CustomerCreditResponse customer, List<CreditSale> credits,
                                    List<ChargeView> charges, List<PaymentView> payments, List<MovementView> movements) {
        static StatementResponse from(CreditViews.Statement value) {
            CreditViews.CustomerRow row = new CreditViews.CustomerRow(value.customer(), value.balance(), value.totalGranted(), value.totalPaid(),
                    value.movements().isEmpty() ? null : value.movements().get(0).date());
            return new StatementResponse(CustomerCreditResponse.from(row), value.sales().stream().map(CreditSale::from).toList(),
                    value.charges().stream().map(ChargeView::from).toList(),
                    value.payments().stream().map(PaymentView::from).toList(), value.movements().stream().map(MovementView::from).toList());
        }
    }
    public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull PaymentMethod method, String notes) {
        CreditPaymentForm toForm() { CreditPaymentForm f = new CreditPaymentForm(); f.setAmount(amount); f.setMethod(method); f.setNotes(notes); return f; }
    }
    public record PaymentResult(Long id, BigDecimal amount, PaymentMethod method, BigDecimal customerBalance, LocalDateTime createdAt) {}
    public record ChargeRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull String concept) {
        CreditChargeForm toForm() { CreditChargeForm form = new CreditChargeForm(); form.setAmount(amount); form.setConcept(concept); return form; }
    }
    public record ChargeResult(Long id, BigDecimal amount, String concept, BigDecimal customerBalance, LocalDateTime createdAt) {}
    public record ReasonRequest(@NotNull String reason) {}
}
