package com.tienda.pos.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancePeriodSummary(
        LocalDate from,
        LocalDate to,
        BigDecimal sales,
        BigDecimal costOfGoodsSold,
        BigDecimal grossProfit,
        BigDecimal expenses,
        BigDecimal netProfit,
        BigDecimal purchases,
        BigDecimal reinvestment,
        BigDecimal ownerContributions,
        BigDecimal ownerWithdrawals,
        long tickets,
        BigDecimal soldUnits
) {
}
