package com.tienda.pos.api.v1.product;

import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductForm;
import com.tienda.pos.product.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class ApiProductModels {

    private ApiProductModels() {
    }

    public record ProductRequest(
            @NotBlank String code,
            String barcode,
            @NotBlank String name,
            String brand,
            String presentation,
            String imageUrl,
            String description,
            Long categoryId,
            @NotNull @DecimalMin("0.00") BigDecimal purchaseCost,
            @NotNull @DecimalMin("0.01") BigDecimal salePrice,
            @NotNull @DecimalMin("0.000") BigDecimal currentStock,
            @NotNull @DecimalMin("0.000") BigDecimal minimumStock,
            @NotNull UnitType unit,
            Long supplierId,
            @NotNull @DecimalMin("0.00") BigDecimal tax,
            @NotNull Boolean active,
            boolean removeImage
    ) {
        public ProductForm toForm(Long id) {
            ProductForm form = new ProductForm();
            form.setId(id);
            form.setCode(code);
            form.setBarcode(barcode);
            form.setName(name);
            form.setBrand(brand);
            form.setPresentation(presentation);
            form.setImageUrl(imageUrl);
            form.setDescription(description);
            form.setCategoryId(categoryId);
            form.setPurchaseCost(purchaseCost);
            form.setSalePrice(salePrice);
            form.setCurrentStock(currentStock);
            form.setMinimumStock(minimumStock);
            form.setUnit(unit);
            form.setSupplierId(supplierId);
            form.setTax(tax);
            form.setActive(active);
            form.setRemoveImage(removeImage);
            return form;
        }
    }

    public record EntityRef(Long id, String name) {
    }

    public record ProductResponse(
            Long id,
            String code,
            String barcode,
            String name,
            String brand,
            String presentation,
            String imageUrl,
            String description,
            EntityRef category,
            BigDecimal purchaseCost,
            BigDecimal salePrice,
            BigDecimal currentStock,
            BigDecimal minimumStock,
            UnitType unit,
            EntityRef supplier,
            BigDecimal tax,
            boolean active,
            boolean promoted,
            Integer promotionOrder,
            boolean promocionWhatsapp,
            boolean lowStock
    ) {
        public static ProductResponse from(Product product) {
            EntityRef category = product.getCategory() == null ? null
                    : new EntityRef(product.getCategory().getId(), product.getCategory().getName());
            EntityRef supplier = product.getSupplier() == null ? null
                    : new EntityRef(product.getSupplier().getId(), product.getSupplier().getName());
            return new ProductResponse(product.getId(), product.getCode(), product.getBarcode(), product.getName(),
                    product.getBrand(), product.getPresentation(), product.getImageUrl(), product.getDescription(),
                    category, product.getPurchaseCost(), product.getSalePrice(), product.getCurrentStock(),
                    product.getMinimumStock(), product.getUnit(), supplier, product.getTax(), product.isActive(),
                    product.isPromoted(), product.getPromotionOrder(), product.isPromocionWhatsapp(), product.hasLowStock());
        }
    }

    public record StateRequest(boolean enabled) {
    }
}
