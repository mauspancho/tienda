package com.tienda.pos.api.v1.purchase;

import com.tienda.pos.purchase.Purchase;
import com.tienda.pos.purchase.PurchaseForm;
import com.tienda.pos.purchase.PurchaseFundingSource;
import com.tienda.pos.purchase.PurchaseItem;
import com.tienda.pos.purchase.PurchaseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiPurchaseModels {

    private ApiPurchaseModels() {
    }

    public record PurchaseCreateRequest(Long supplierId, String externalFolio,
                                        @NotNull Long productId,
                                        @NotNull @DecimalMin("0.001") BigDecimal quantity,
                                        @NotNull @DecimalMin("0.00") BigDecimal unitCost,
                                        boolean updateProductCost,
                                        @NotNull PurchaseFundingSource fundingSource,
                                        String notes) {
        public PurchaseForm toDomain() {
            PurchaseForm form = new PurchaseForm();
            form.setSupplierId(supplierId);
            form.setExternalFolio(externalFolio);
            form.setProductId(productId);
            form.setQuantity(quantity);
            form.setUnitCost(unitCost);
            form.setUpdateProductCost(updateProductCost);
            form.setFundingSource(fundingSource);
            form.setNotes(notes);
            return form;
        }
    }

    public record EntityRef(Long id, String name) {
    }

    public record PurchaseSummary(Long id, LocalDate purchaseDate, String externalFolio,
                                  EntityRef supplier, PurchaseStatus status,
                                  PurchaseFundingSource fundingSource, BigDecimal total) {
        public static PurchaseSummary from(Purchase purchase) {
            EntityRef supplier = purchase.getSupplier() == null ? null
                    : new EntityRef(purchase.getSupplier().getId(), purchase.getSupplier().getName());
            return new PurchaseSummary(purchase.getId(), purchase.getPurchaseDate(), purchase.getExternalFolio(),
                    supplier, purchase.getStatus(), purchase.getFundingSource(), purchase.getTotal());
        }
    }

    public record PurchaseLineResponse(Long productId, String productName, BigDecimal quantity,
                                       BigDecimal unitCost, BigDecimal subtotal) {
        static PurchaseLineResponse from(PurchaseItem item) {
            return new PurchaseLineResponse(item.getProduct().getId(), item.getProduct().getName(),
                    item.getQuantity(), item.getUnitCost(), item.getSubtotal());
        }
    }

    public record PurchaseResponse(Long id, LocalDate purchaseDate, String externalFolio,
                                   EntityRef supplier, EntityRef user, PurchaseStatus status,
                                   PurchaseFundingSource fundingSource, BigDecimal subtotal,
                                   BigDecimal tax, BigDecimal total, String notes,
                                   List<PurchaseLineResponse> items) {
        public static PurchaseResponse from(Purchase purchase) {
            EntityRef supplier = purchase.getSupplier() == null ? null
                    : new EntityRef(purchase.getSupplier().getId(), purchase.getSupplier().getName());
            EntityRef user = purchase.getUser() == null ? null
                    : new EntityRef(purchase.getUser().getId(), purchase.getUser().fullName());
            return new PurchaseResponse(purchase.getId(), purchase.getPurchaseDate(), purchase.getExternalFolio(),
                    supplier, user, purchase.getStatus(), purchase.getFundingSource(), purchase.getSubtotal(),
                    purchase.getTax(), purchase.getTotal(), purchase.getNotes(),
                    purchase.getItems().stream().map(PurchaseLineResponse::from).toList());
        }
    }
}
