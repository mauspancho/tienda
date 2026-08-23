package com.tienda.pos.sale;

import com.tienda.pos.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SaleRequest {
    private Long customerId;
    @DecimalMin("0.00")
    private BigDecimal discount = BigDecimal.ZERO;
    @NotNull
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    @DecimalMin("0.00")
    private BigDecimal receivedAmount = BigDecimal.ZERO;
    @Valid
    @NotEmpty
    private List<SaleLineRequest> items = new ArrayList<>();

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(BigDecimal receivedAmount) { this.receivedAmount = receivedAmount; }
    public List<SaleLineRequest> getItems() { return items; }
    public void setItems(List<SaleLineRequest> items) { this.items = items; }

    public static class SaleLineRequest {
        @NotNull
        private Long productId;
        @NotNull
        @DecimalMin("0.001")
        private BigDecimal quantity;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }
}
