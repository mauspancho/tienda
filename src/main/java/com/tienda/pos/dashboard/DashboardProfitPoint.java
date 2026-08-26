package com.tienda.pos.dashboard;

import java.math.BigDecimal;

public record DashboardProfitPoint(
        String label,
        BigDecimal profit
) {
}
