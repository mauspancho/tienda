package com.tienda.pos.dashboard;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.inventory.InventoryMovementRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.sale.SaleStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@NormalMode
public class DashboardService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "MX"));

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ExpenseRepository expenseRepository;
    private final InventoryMovementRepository movementRepository;

    public DashboardService(SaleRepository saleRepository, ProductRepository productRepository,
                            ExpenseRepository expenseRepository, InventoryMovementRepository movementRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.expenseRepository = expenseRepository;
        this.movementRepository = movementRepository;
    }

    public DashboardSummary today() {
        return today(null, true);
    }

    public DashboardSummary today(String username, boolean admin) {
        LocalDate today = LocalDate.now();
        var start = today.atStartOfDay();
        var end = today.plusDays(1).atStartOfDay().minusNanos(1);
        return new DashboardSummary(
                admin ? saleRepository.totalSales(start, end) : saleRepository.totalSalesByCashier(username, start, end),
                admin ? saleRepository.grossProfit(start, end) : saleRepository.grossProfitByCashier(username, start, end),
                admin ? saleRepository.soldUnits(start, end) : saleRepository.soldUnitsByCashier(username, start, end),
                admin ? saleRepository.countBySaleDateBetweenAndStatus(start, end, SaleStatus.COMPLETED)
                        : saleRepository.countByCashierUsernameAndSaleDateBetweenAndStatus(username, start, end, SaleStatus.COMPLETED),
                productRepository.findLowStock(PageRequest.of(0, 100)).size(),
                expenseRepository.totalBetween(today, today),
                productRepository.inventoryValue(),
                admin ? saleRepository.dailySalesSince(today.minusDays(6).atStartOfDay())
                        : saleRepository.dailySalesSinceByCashier(username, today.minusDays(6).atStartOfDay()),
                admin ? saleRepository.topProducts(start, end, PageRequest.of(0, 5))
                        : saleRepository.topProductsByCashier(username, start, end, PageRequest.of(0, 5))
        );
    }

    public DashboardProfitAnalysis profitAnalysis(LocalDate selectedDate, String period, String username, boolean admin) {
        LocalDate date = selectedDate == null ? LocalDate.now() : selectedDate;
        ProfitPeriod profitPeriod = ProfitPeriod.from(period);
        LocalDate periodStart = periodStart(date, profitPeriod);
        LocalDate periodEnd = periodEnd(date, profitPeriod);
        LocalDate seriesStart = seriesStart(date, profitPeriod);
        LocalDate seriesEnd = periodEnd;
        Map<LocalDate, BigDecimal> daily = dailyProfitMap(seriesStart, seriesEnd, username, admin);
        BigDecimal selectedDateProfit = daily.getOrDefault(date, BigDecimal.ZERO);
        BigDecimal selectedDateCostAdjustment = admin ? costAdjustmentBetween(date, date) : BigDecimal.ZERO;
        List<DashboardProfitPoint> points = points(daily, seriesStart, seriesEnd, profitPeriod);
        BigDecimal periodProfit = points.stream()
                .filter(point -> periodLabel(date, profitPeriod).equals(point.label()))
                .map(DashboardProfitPoint::profit)
                .findFirst()
                .orElseGet(() -> sumRange(daily, periodStart, periodEnd));
        BigDecimal periodCostAdjustment = admin ? costAdjustmentBetween(periodStart, periodEnd) : BigDecimal.ZERO;
        return new DashboardProfitAnalysis(
                date,
                selectedDateProfit,
                selectedDateCostAdjustment,
                profitPeriod.value,
                periodLabel(date, profitPeriod),
                periodProfit,
                periodCostAdjustment,
                points
        );
    }

    private BigDecimal costAdjustmentBetween(LocalDate start, LocalDate end) {
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.plusDays(1).atStartOfDay().minusNanos(1);
        return movementRepository.costAdjustmentBetween(startAt, endAt);
    }

    private Map<LocalDate, BigDecimal> dailyProfitMap(LocalDate start, LocalDate end, String username, boolean admin) {
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.plusDays(1).atStartOfDay().minusNanos(1);
        List<Object[]> rows = admin
                ? saleRepository.dailyGrossProfitBetween(startAt, endAt)
                : saleRepository.dailyGrossProfitBetweenByCashier(username, startAt, endAt);
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(toLocalDate(row[0]), (BigDecimal) row[1]);
        }
        return result;
    }

    private List<DashboardProfitPoint> points(Map<LocalDate, BigDecimal> daily, LocalDate start, LocalDate end, ProfitPeriod period) {
        List<DashboardProfitPoint> points = new ArrayList<>();
        if (period == ProfitPeriod.DAY) {
            for (LocalDate current = start; !current.isAfter(end); current = current.plusDays(1)) {
                points.add(new DashboardProfitPoint(current.format(DAY_LABEL), daily.getOrDefault(current, BigDecimal.ZERO)));
            }
            return points;
        }
        if (period == ProfitPeriod.WEEK) {
            for (LocalDate current = startOfWeek(start); !current.isAfter(end); current = current.plusWeeks(1)) {
                LocalDate weekEnd = current.plusDays(6);
                points.add(new DashboardProfitPoint(weekLabel(current), sumRange(daily, current, weekEnd)));
            }
            return points;
        }
        YearMonth first = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        for (YearMonth current = first; !current.isAfter(last); current = current.plusMonths(1)) {
            points.add(new DashboardProfitPoint(monthLabel(current), sumRange(daily, current.atDay(1), current.atEndOfMonth())));
        }
        return points;
    }

    private BigDecimal sumRange(Map<LocalDate, BigDecimal> daily, LocalDate start, LocalDate end) {
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate current = start; !current.isAfter(end); current = current.plusDays(1)) {
            total = total.add(daily.getOrDefault(current, BigDecimal.ZERO));
        }
        return total;
    }

    private LocalDate seriesStart(LocalDate date, ProfitPeriod period) {
        return switch (period) {
            case DAY -> date.minusDays(13);
            case WEEK -> startOfWeek(date).minusWeeks(7);
            case MONTH -> YearMonth.from(date).minusMonths(11).atDay(1);
        };
    }

    private LocalDate periodStart(LocalDate date, ProfitPeriod period) {
        return switch (period) {
            case DAY -> date;
            case WEEK -> startOfWeek(date);
            case MONTH -> YearMonth.from(date).atDay(1);
        };
    }

    private LocalDate periodEnd(LocalDate date, ProfitPeriod period) {
        return switch (period) {
            case DAY -> date;
            case WEEK -> startOfWeek(date).plusDays(6);
            case MONTH -> YearMonth.from(date).atEndOfMonth();
        };
    }

    private String periodLabel(LocalDate date, ProfitPeriod period) {
        return switch (period) {
            case DAY -> date.format(DAY_LABEL);
            case WEEK -> weekLabel(startOfWeek(date));
            case MONTH -> monthLabel(YearMonth.from(date));
        };
    }

    private String weekLabel(LocalDate weekStart) {
        return weekStart.format(DAY_LABEL) + " - " + weekStart.plusDays(6).format(DAY_LABEL);
    }

    private String monthLabel(YearMonth month) {
        return month.format(MONTH_LABEL).replace(".", "");
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private enum ProfitPeriod {
        DAY("day"),
        WEEK("week"),
        MONTH("month");

        private final String value;

        ProfitPeriod(String value) {
            this.value = value;
        }

        private static ProfitPeriod from(String value) {
            if (value == null || value.isBlank()) {
                return DAY;
            }
            for (ProfitPeriod period : values()) {
                if (period.value.equalsIgnoreCase(value)) {
                    return period;
                }
            }
            return DAY;
        }
    }
}

