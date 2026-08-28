package com.tienda.pos.finance;

public enum CapitalMovementType {
    INITIAL_INVESTMENT("Inversión inicial", true),
    OWNER_CONTRIBUTION("Aportación propietario", true),
    REINVESTMENT("Reinversión", false),
    OWNER_WITHDRAWAL("Retiro propietario", true),
    CAPITAL_ADJUSTMENT("Ajuste de capital", true);

    private final String label;
    private final boolean manual;

    CapitalMovementType(String label, boolean manual) {
        this.label = label;
        this.manual = manual;
    }

    public String getLabel() {
        return label;
    }

    public boolean isManual() {
        return manual;
    }
}
