package com.tienda.pos.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportSummary(String period, LocalDate from, LocalDate to, BigDecimal sales,
                            BigDecimal grossProfit, BigDecimal expenses, BigDecimal result,
                            BigDecimal inventoryValue, List<Object[]> topProducts) {
}
