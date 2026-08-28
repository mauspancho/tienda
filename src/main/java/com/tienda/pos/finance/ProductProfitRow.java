package com.tienda.pos.finance;

import java.math.BigDecimal;

public record ProductProfitRow(
        String product,
        BigDecimal sales,
        BigDecimal quantity,
        BigDecimal costOfGoodsSold,
        BigDecimal profit,
        BigDecimal marginPercent
) {
}
