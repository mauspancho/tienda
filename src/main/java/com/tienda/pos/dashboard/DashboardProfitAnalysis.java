package com.tienda.pos.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardProfitAnalysis(
        LocalDate selectedDate,
        BigDecimal selectedDateProfit,
        String period,
        String periodLabel,
        BigDecimal periodProfit,
        List<DashboardProfitPoint> points
) {
}
