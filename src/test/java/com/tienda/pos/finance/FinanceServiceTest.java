package com.tienda.pos.finance;

import com.tienda.pos.expense.ExpenseRepository;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.purchase.PurchaseFundingSource;
import com.tienda.pos.purchase.PurchaseRepository;
import com.tienda.pos.sale.SaleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceServiceTest {

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CapitalMovementRepository capitalMovementRepository = mock(CapitalMovementRepository.class);
    private final FinanceService service = new FinanceService(saleRepository, expenseRepository, purchaseRepository,
            productRepository, capitalMovementRepository);

    @Test
    void summaryUsesHistoricalSaleItemCostsAndSeparatesReinvestmentFromOwnerCapital() {
        when(saleRepository.financeTotals(any(), any()))
                .thenReturn(new Object[]{new BigDecimal("100.00"), new BigDecimal("70.00"), new BigDecimal("30.00"), 1L, new BigDecimal("5.00")});
        when(saleRepository.dailyFinanceTotals(any(), any())).thenReturn(List.<Object[]>of());
        when(saleRepository.profitableProducts(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Frijol", new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("70.00"), new BigDecimal("30.00")}
        ));
        when(expenseRepository.totalBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(new BigDecimal("10.00"));
        when(expenseRepository.dailyTotalsBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(purchaseRepository.totalBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(new BigDecimal("100.00"));
        when(purchaseRepository.totalByFundingSourceBetween(eq(PurchaseFundingSource.BUSINESS_CASH), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("40.00"));
        when(purchaseRepository.totalByFundingSourceBetween(eq(PurchaseFundingSource.OWNER_CAPITAL), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("60.00"));
        when(purchaseRepository.dailyTotalsBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(purchaseRepository.dailyTotalsByFundingSourceBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(purchaseRepository.monthlyTotalsByFundingSourceBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(capitalMovementRepository.totalByType(any())).thenReturn(BigDecimal.ZERO);
        when(capitalMovementRepository.totalByTypeBetween(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(capitalMovementRepository.dailyCapitalTotals(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(capitalMovementRepository.monthlyCapitalTotals(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.<Object[]>of());
        when(productRepository.inventoryValue()).thenReturn(new BigDecimal("300.00"));
        when(productRepository.inventorySaleValue()).thenReturn(new BigDecimal("500.00"));

        FinanceSummary summary = service.summary("TODAY", null, null, "profit");

        assertThat(summary.period().sales()).isEqualByComparingTo("100.00");
        assertThat(summary.period().costOfGoodsSold()).isEqualByComparingTo("70.00");
        assertThat(summary.period().grossProfit()).isEqualByComparingTo("30.00");
        assertThat(summary.period().netProfit()).isEqualByComparingTo("20.00");
        assertThat(summary.period().reinvestment()).isEqualByComparingTo("40.00");
        assertThat(summary.period().ownerContributions()).isEqualByComparingTo("60.00");
        assertThat(summary.inventoryPotentialProfit()).isEqualByComparingTo("200.00");
        assertThat(summary.products()).hasSize(1);
        assertThat(summary.products().get(0).marginPercent()).isEqualByComparingTo("30.00");
    }
}