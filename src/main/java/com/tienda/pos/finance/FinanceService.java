package com.tienda.pos.finance;

import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.purchase.PurchaseFundingSource;
import com.tienda.pos.purchase.PurchaseRepository;
import com.tienda.pos.sale.SaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@NormalMode
public class FinanceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "MX"));

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final CapitalMovementRepository capitalMovementRepository;

    public FinanceService(SaleRepository saleRepository, ExpenseRepository expenseRepository,
                          PurchaseRepository purchaseRepository, ProductRepository productRepository,
                          CapitalMovementRepository capitalMovementRepository) {
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.capitalMovementRepository = capitalMovementRepository;
    }

    public FinanceSummary summary(String period, LocalDate from, LocalDate to, String productSort) {
        FinanceRange range = range(period, from, to);
        LocalDate today = today();
        FinancePeriodSummary todaySummary = periodSummary(today, today);
        FinancePeriodSummary selected = periodSummary(range.from(), range.to());
        FinancePeriodSummary accumulated = periodSummary(LocalDate.of(1970, 1, 1), today);

        BigDecimal initialInvestment = money(capitalMovementRepository.totalByType(CapitalMovementType.INITIAL_INVESTMENT));
        BigDecimal manualContributions = money(capitalMovementRepository.totalByType(CapitalMovementType.OWNER_CONTRIBUTION));
        BigDecimal ownerCapitalPurchases = money(purchaseRepository.totalByFundingSourceBetween(
                PurchaseFundingSource.OWNER_CAPITAL, LocalDate.of(1970, 1, 1), today));
        BigDecimal additionalContributions = money(manualContributions.add(ownerCapitalPurchases));
        BigDecimal capitalContributedTotal = money(initialInvestment.add(additionalContributions));
        BigDecimal ownerWithdrawalsTotal = money(capitalMovementRepository.totalByType(CapitalMovementType.OWNER_WITHDRAWAL));
        BigDecimal capitalAdjustments = money(capitalMovementRepository.totalByType(CapitalMovementType.CAPITAL_ADJUSTMENT));
        BigDecimal netProfitAccumulated = accumulated.netProfit();
        BigDecimal retainedProfit = money(netProfitAccumulated.subtract(ownerWithdrawalsTotal));
        BigDecimal capitalInsideBusiness = money(capitalContributedTotal.add(netProfitAccumulated).add(capitalAdjustments).subtract(ownerWithdrawalsTotal));

        BigDecimal inventoryCost = money(productRepository.inventoryValue());
        BigDecimal inventorySaleValue = money(productRepository.inventorySaleValue());
        BigDecimal inventoryPotentialProfit = money(inventorySaleValue.subtract(inventoryCost));
        BigDecimal recoveryPercent = recoveryPercent(netProfitAccumulated, initialInvestment);
        BigDecimal recoveryRemaining = initialInvestment.compareTo(BigDecimal.ZERO) > 0
                ? money(initialInvestment.subtract(netProfitAccumulated).max(BigDecimal.ZERO))
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashIn = money(selected.sales().add(selected.ownerContributions()));
        BigDecimal cashOut = money(selected.reinvestment().add(selected.expenses()).add(selected.ownerWithdrawals()));
        BigDecimal cashFlow = money(cashIn.subtract(cashOut));

        return new FinanceSummary(
                range,
                todaySummary,
                selected,
                initialInvestment,
                additionalContributions,
                capitalContributedTotal,
                ownerWithdrawalsTotal,
                retainedProfit,
                netProfitAccumulated,
                capitalInsideBusiness,
                inventoryCost,
                inventorySaleValue,
                inventoryPotentialProfit,
                recoveryPercent,
                recoveryRemaining,
                cashIn,
                cashOut,
                cashFlow,
                dailySummaries(range.from(), range.to()),
                monthlyReinvestment(range.from().minusMonths(11).withDayOfMonth(1), range.to()),
                monthlyCapital(range.from().minusMonths(11).withDayOfMonth(1), range.to()),
                productRows(range.from(), range.to(), productSort)
        );
    }

    public List<DailyFinanceSummary> daily(LocalDate from, LocalDate to) {
        FinanceRange range = range("CUSTOM", from, to);
        return dailySummaries(range.from(), range.to());
    }

    public FinancePeriodSummary detail(LocalDate date) {
        return periodSummary(date, date);
    }

    @Transactional
    public CapitalMovement registerCapitalMovement(CapitalMovementForm form) {
        if (form.getType() == null || !form.getType().isManual() || form.getType() == CapitalMovementType.REINVESTMENT) {
            throw new DomainException("Este tipo de movimiento no se registra manualmente.");
        }
        if (form.getType() == CapitalMovementType.INITIAL_INVESTMENT
                && capitalMovementRepository.existsByType(CapitalMovementType.INITIAL_INVESTMENT)) {
            throw new DomainException("Ya existe una inversión inicial. Usa un ajuste de capital si necesitas corregirla.");
        }
        CapitalMovement movement = new CapitalMovement();
        movement.setMovementDate(form.getMovementDate() == null ? today() : form.getMovementDate());
        movement.setType(form.getType());
        movement.setAmount(money(form.getAmount()));
        movement.setDescription(form.getDescription());
        return capitalMovementRepository.save(movement);
    }

    public FinanceRange range(String period, LocalDate from, LocalDate to) {
        LocalDate now = today();
        String normalized = period == null || period.isBlank() ? "LAST_30_DAYS" : period;
        FinanceRange candidate = switch (normalized) {
            case "TODAY" -> new FinanceRange(now, now, "Hoy", normalized);
            case "YESTERDAY" -> new FinanceRange(now.minusDays(1), now.minusDays(1), "Ayer", normalized);
            case "LAST_7_DAYS" -> new FinanceRange(now.minusDays(6), now, "Últimos 7 días", normalized);
            case "THIS_MONTH" -> new FinanceRange(now.withDayOfMonth(1), now, "Este mes", normalized);
            case "PREVIOUS_MONTH" -> {
                YearMonth previous = YearMonth.from(now).minusMonths(1);
                yield new FinanceRange(previous.atDay(1), previous.atEndOfMonth(), "Mes anterior", normalized);
            }
            case "THIS_YEAR" -> new FinanceRange(now.withDayOfYear(1), now, "Este año", normalized);
            case "CUSTOM" -> new FinanceRange(from == null ? now : from, to == null ? now : to, "Personalizado", normalized);
            default -> new FinanceRange(now.minusDays(29), now, "Últimos 30 días", "LAST_30_DAYS");
        };
        if (candidate.from().isAfter(candidate.to())) {
            return new FinanceRange(candidate.to(), candidate.from(), candidate.label(), candidate.period());
        }
        return candidate;
    }

    private FinancePeriodSummary periodSummary(LocalDate from, LocalDate to) {
        Object[] sales = saleRepository.financeTotals(start(from), end(to));
        BigDecimal grossProfit = money(value(sales, 2));
        BigDecimal expenses = money(expenseRepository.totalBetween(from, to));
        BigDecimal netProfit = money(grossProfit.subtract(expenses));
        BigDecimal purchases = money(purchaseRepository.totalBetween(from, to));
        BigDecimal reinvestment = money(purchaseRepository.totalByFundingSourceBetween(PurchaseFundingSource.BUSINESS_CASH, from, to));
        BigDecimal ownerCapitalPurchases = money(purchaseRepository.totalByFundingSourceBetween(PurchaseFundingSource.OWNER_CAPITAL, from, to));
        BigDecimal ownerContributions = money(capitalMovementRepository.totalByTypeBetween(CapitalMovementType.OWNER_CONTRIBUTION, from, to).add(ownerCapitalPurchases));
        BigDecimal ownerWithdrawals = money(capitalMovementRepository.totalByTypeBetween(CapitalMovementType.OWNER_WITHDRAWAL, from, to));
        return new FinancePeriodSummary(from, to, money(value(sales, 0)), money(value(sales, 1)), grossProfit, expenses,
                netProfit, purchases, reinvestment, ownerContributions, ownerWithdrawals, longValue(sales, 3), money(value(sales, 4)));
    }

    private List<DailyFinanceSummary> dailySummaries(LocalDate from, LocalDate to) {
        Map<LocalDate, Object[]> sales = byDate(saleRepository.dailyFinanceTotals(start(from), end(to)));
        Map<LocalDate, BigDecimal> expenses = amountByDate(expenseRepository.dailyTotalsBetween(from, to));
        Map<LocalDate, BigDecimal> purchases = amountByDate(purchaseRepository.dailyTotalsBetween(from, to));
        Map<LocalDate, BigDecimal> reinvestment = amountByDate(purchaseRepository.dailyTotalsByFundingSourceBetween(PurchaseFundingSource.BUSINESS_CASH, from, to));
        Map<LocalDate, BigDecimal> ownerCapitalPurchases = amountByDate(purchaseRepository.dailyTotalsByFundingSourceBetween(PurchaseFundingSource.OWNER_CAPITAL, from, to));
        Map<LocalDate, CapitalDay> capital = capitalByDate(from, to);
        List<DailyFinanceSummary> days = new ArrayList<>();
        for (LocalDate current = from; !current.isAfter(to); current = current.plusDays(1)) {
            Object[] row = sales.get(current);
            BigDecimal gross = money(value(row, 3));
            BigDecimal dayExpenses = money(expenses.getOrDefault(current, BigDecimal.ZERO));
            CapitalDay capitalDay = capital.getOrDefault(current, CapitalDay.ZERO);
            BigDecimal contributions = money(capitalDay.contributions().add(ownerCapitalPurchases.getOrDefault(current, BigDecimal.ZERO)));
            days.add(new DailyFinanceSummary(current, money(value(row, 1)), money(value(row, 2)), gross, dayExpenses,
                    money(gross.subtract(dayExpenses)), money(purchases.getOrDefault(current, BigDecimal.ZERO)),
                    money(reinvestment.getOrDefault(current, BigDecimal.ZERO)), contributions,
                    money(capitalDay.withdrawals()), longValue(row, 4), money(value(row, 5))));
        }
        return days;
    }

    private List<FinanceChartPoint> monthlyReinvestment(LocalDate from, LocalDate to) {
        Map<YearMonth, BigDecimal> monthly = monthAmounts(purchaseRepository.monthlyTotalsByFundingSourceBetween(PurchaseFundingSource.BUSINESS_CASH, from, to));
        return monthSeries(from, to).stream()
                .map(month -> new FinanceChartPoint(month.format(MONTH_LABEL).replace(".", ""), money(monthly.getOrDefault(month, BigDecimal.ZERO)), BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();
    }

    private List<FinanceChartPoint> monthlyCapital(LocalDate from, LocalDate to) {
        Map<YearMonth, CapitalDay> capital = capitalByMonth(from, to);
        List<FinanceChartPoint> points = new ArrayList<>();
        BigDecimal contributed = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        for (YearMonth month : monthSeries(from, to)) {
            CapitalDay row = capital.getOrDefault(month, CapitalDay.ZERO);
            contributed = money(contributed.add(row.contributions()));
            withdrawals = money(withdrawals.add(row.withdrawals()));
            LocalDate monthEnd = month.atEndOfMonth().isAfter(to) ? to : month.atEndOfMonth();
            BigDecimal cumulativeProfit = money(periodSummary(LocalDate.of(1970, 1, 1), monthEnd).netProfit());
            points.add(new FinanceChartPoint(month.format(MONTH_LABEL).replace(".", ""), contributed, cumulativeProfit, withdrawals));
        }
        return points;
    }

    private List<ProductProfitRow> productRows(LocalDate from, LocalDate to, String sort) {
        String normalized = sort == null ? "profit" : sort;
        List<Object[]> rows = switch (normalized) {
            case "quantity" -> saleRepository.mostSoldProducts(start(from), end(to), PageRequest.of(0, 12));
            case "sales" -> saleRepository.topBillingProducts(start(from), end(to), PageRequest.of(0, 12));
            default -> saleRepository.profitableProducts(start(from), end(to), PageRequest.of(0, 12));
        };
        return rows.stream()
                .map(row -> {
                    BigDecimal sales = money(value(row, 1));
                    BigDecimal profit = money(value(row, 4));
                    BigDecimal margin = sales.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                            : money(profit.multiply(MoneyUtils.ONE_HUNDRED).divide(sales, 2, RoundingMode.HALF_UP));
                    return new ProductProfitRow(String.valueOf(row[0]), sales, money(value(row, 2)), money(value(row, 3)), profit, margin);
                })
                .toList();
    }

    private Map<LocalDate, CapitalDay> capitalByDate(LocalDate from, LocalDate to) {
        Map<LocalDate, CapitalDay> result = new LinkedHashMap<>();
        for (Object[] row : capitalMovementRepository.dailyCapitalTotals(from, to)) {
            LocalDate date = toLocalDate(row[0]);
            CapitalMovementType type = (CapitalMovementType) row[1];
            BigDecimal amount = money(value(row, 2));
            result.merge(date, CapitalDay.of(type, amount), CapitalDay::add);
        }
        return result;
    }

    private Map<YearMonth, CapitalDay> capitalByMonth(LocalDate from, LocalDate to) {
        Map<YearMonth, CapitalDay> result = new LinkedHashMap<>();
        for (Object[] row : capitalMovementRepository.monthlyCapitalTotals(from, to)) {
            YearMonth month = YearMonth.of(number(row[0]).intValue(), number(row[1]).intValue());
            CapitalMovementType type = (CapitalMovementType) row[2];
            BigDecimal amount = money(value(row, 3));
            result.merge(month, CapitalDay.of(type, amount), CapitalDay::add);
        }
        return result;
    }

    private Map<LocalDate, Object[]> byDate(List<Object[]> rows) {
        Map<LocalDate, Object[]> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(toLocalDate(row[0]), row);
        }
        return result;
    }

    private Map<LocalDate, BigDecimal> amountByDate(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(toLocalDate(row[0]), money(value(row, 1)));
        }
        return result;
    }

    private Map<YearMonth, BigDecimal> monthAmounts(List<Object[]> rows) {
        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(YearMonth.of(number(row[0]).intValue(), number(row[1]).intValue()), money(value(row, 2)));
        }
        return result;
    }

    private List<YearMonth> monthSeries(LocalDate from, LocalDate to) {
        YearMonth first = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth current = first; !current.isAfter(last); current = current.plusMonths(1)) {
            months.add(current);
        }
        return months;
    }

    private BigDecimal recoveryPercent(BigDecimal netProfit, BigDecimal initialInvestment) {
        if (initialInvestment.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal recovered = netProfit.max(BigDecimal.ZERO);
        return money(recovered.multiply(MoneyUtils.ONE_HUNDRED).divide(initialInvestment, 2, RoundingMode.HALF_UP));
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private LocalDateTime start(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime end(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusNanos(1);
    }

    private BigDecimal money(BigDecimal value) {
        return MoneyUtils.money(value);
    }

    private BigDecimal value(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object value = row[index];
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private long longValue(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0L;
        }
        Object value = row[index];
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
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

    private record CapitalDay(BigDecimal contributions, BigDecimal withdrawals) {
        private static final CapitalDay ZERO = new CapitalDay(BigDecimal.ZERO, BigDecimal.ZERO);

        private static CapitalDay of(CapitalMovementType type, BigDecimal amount) {
            return switch (type) {
                case INITIAL_INVESTMENT, OWNER_CONTRIBUTION, CAPITAL_ADJUSTMENT -> new CapitalDay(amount, BigDecimal.ZERO);
                case OWNER_WITHDRAWAL -> new CapitalDay(BigDecimal.ZERO, amount);
                case REINVESTMENT -> ZERO;
            };
        }

        private CapitalDay add(CapitalDay other) {
            return new CapitalDay(contributions.add(other.contributions), withdrawals.add(other.withdrawals));
        }
    }
}
