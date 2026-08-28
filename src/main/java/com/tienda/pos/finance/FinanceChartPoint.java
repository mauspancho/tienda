package com.tienda.pos.finance;

import java.math.BigDecimal;

public record FinanceChartPoint(String label, BigDecimal value, BigDecimal secondary, BigDecimal tertiary) {
}
