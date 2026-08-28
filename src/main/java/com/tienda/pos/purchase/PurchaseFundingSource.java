package com.tienda.pos.purchase;

public enum PurchaseFundingSource {
    BUSINESS_CASH("Caja / ventas"),
    OWNER_CAPITAL("Capital propietario"),
    SUPPLIER_CREDIT("Crédito proveedor"),
    OTHER("Otro"),
    UNKNOWN("Sin clasificar");

    private final String label;

    PurchaseFundingSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
