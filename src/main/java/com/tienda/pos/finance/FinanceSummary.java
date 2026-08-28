package com.tienda.pos.finance;

import java.math.BigDecimal;
import java.util.List;

public record FinanceSummary(
        FinanceRange range,
        FinancePeriodSummary today,
        FinancePeriodSummary period,
        BigDecimal initialInvestment,
        BigDecimal additionalContributions,
        BigDecimal capitalContributedTotal,
        BigDecimal ownerWithdrawalsTotal,
        BigDecimal retainedProfit,
        BigDecimal netProfitAccumulated,
        BigDecimal capitalInsideBusiness,
        BigDecimal inventoryCost,
        BigDecimal inventorySaleValue,
        BigDecimal inventoryPotentialProfit,
        BigDecimal recoveryPercent,
        BigDecimal recoveryRemaining,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal cashFlow,
        List<DailyFinanceSummary> daily,
        List<FinanceChartPoint> monthlyReinvestment,
        List<FinanceChartPoint> monthlyCapital,
        List<ProductProfitRow> products
) {
}
