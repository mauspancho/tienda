package com.tienda.pos.api.v1.sale;

import com.tienda.pos.payment.Payment;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.sale.Sale;
import com.tienda.pos.sale.SaleItem;
import com.tienda.pos.sale.SaleRequest;
import com.tienda.pos.sale.SaleStatus;
import com.tienda.pos.sale.SettlementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiSaleModels {

    private ApiSaleModels() {
    }

    public record SaleLineRequest(@NotNull Long productId,
                                  @NotNull @DecimalMin("0.001") BigDecimal quantity) {
    }

    public record SaleCreateRequest(Long customerId,
                                    SettlementType settlementType,
                                    @NotNull @DecimalMin("0.00") BigDecimal discount,
                                    @NotNull PaymentMethod paymentMethod,
                                    @NotNull @DecimalMin("0.00") BigDecimal receivedAmount,
                                    @Valid @NotEmpty List<SaleLineRequest> items) {
        public SaleRequest toDomain() {
            SaleRequest request = new SaleRequest();
            request.setCustomerId(customerId);
            request.setSettlementType(settlementType == null ? SettlementType.IMMEDIATE : settlementType);
            request.setDiscount(discount);
            request.setPaymentMethod(paymentMethod);
            request.setReceivedAmount(receivedAmount);
            request.setItems(items.stream().map(line -> {
                SaleRequest.SaleLineRequest target = new SaleRequest.SaleLineRequest();
                target.setProductId(line.productId());
                target.setQuantity(line.quantity());
                return target;
            }).toList());
            return request;
        }
    }

    public record PartyRef(Long id, String name) {
    }

    public record SaleLineResponse(Long productId, String productName, BigDecimal quantity,
                                   BigDecimal unitPrice, BigDecimal unitCost, BigDecimal discount,
                                   BigDecimal subtotal, BigDecimal profit) {
        static SaleLineResponse from(SaleItem item) {
            return new SaleLineResponse(item.getProduct().getId(), item.getProductNameSnapshot(), item.getQuantity(),
                    item.getUnitPrice(), item.getUnitCost(), item.getDiscount(), item.getSubtotal(), item.getProfit());
        }
    }

    public record PaymentResponse(PaymentMethod method, BigDecimal amount,
                                  BigDecimal receivedAmount, BigDecimal changeAmount) {
        static PaymentResponse from(Payment payment) {
            return payment == null ? null : new PaymentResponse(payment.getMethod(), payment.getAmount(),
                    payment.getReceivedAmount(), payment.getChangeAmount());
        }
    }

    public record SaleResponse(Long id, String folio, LocalDateTime saleDate, PartyRef cashier,
                               PartyRef customer, BigDecimal subtotal, BigDecimal discount,
                               BigDecimal tax, BigDecimal total, SaleStatus status,
                               SettlementType settlementType, BigDecimal amountPaid, BigDecimal pendingBalance,
                               PaymentResponse payment, List<SaleLineResponse> items) {
        public static SaleResponse from(Sale sale) {
            return from(sale, BigDecimal.ZERO);
        }

        public static SaleResponse from(Sale sale, BigDecimal creditPaid) {
            PartyRef cashier = sale.getCashier() == null ? null
                    : new PartyRef(sale.getCashier().getId(), sale.getCashier().fullName());
            PartyRef customer = sale.getCustomer() == null ? null
                    : new PartyRef(sale.getCustomer().getId(), sale.getCustomer().getName());
            BigDecimal paid = sale.getSettlementType() == SettlementType.CREDIT ? creditPaid : sale.getTotal();
            BigDecimal pending = sale.getSettlementType() == SettlementType.CREDIT && sale.getStatus() == SaleStatus.COMPLETED
                    ? sale.getTotal().subtract(creditPaid) : BigDecimal.ZERO;
            return new SaleResponse(sale.getId(), sale.getFolio(), sale.getSaleDate(), cashier, customer,
                    sale.getSubtotal(), sale.getDiscount(), sale.getTax(), sale.getTotal(), sale.getStatus(),
                    sale.getSettlementType(), paid, pending,
                    PaymentResponse.from(sale.getPayment()), sale.getItems().stream().map(SaleLineResponse::from).toList());
        }
    }
}
