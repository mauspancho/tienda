package com.tienda.pos.finance;

import com.tienda.pos.common.DisplayFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DisplayFormat.class)
class FinanceControllerRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceService financeService;

    @MockBean
    private CapitalMovementRepository capitalMovementRepository;

    @Test
    void financeIndexRendersWithSummaryData() throws Exception {
        LocalDate today = LocalDate.of(2026, 8, 27);
        FinancePeriodSummary period = new FinancePeriodSummary(today, today, money("100.00"), money("70.00"),
                money("30.00"), money("10.00"), money("20.00"), money("40.00"), money("25.00"),
                money("15.00"), money("5.00"), 2L, money("4.00"));
        FinanceSummary summary = new FinanceSummary(
                new FinanceRange(today, today, "Hoy", "TODAY"), period, period,
                money("1000.00"), money("150.00"), money("1150.00"), money("50.00"), money("20.00"),
                money("70.00"), money("1170.00"), money("300.00"), money("500.00"), money("200.00"),
                money("7.00"), money("930.00"), money("115.00"), money("80.00"), money("35.00"),
                List.of(new DailyFinanceSummary(today, money("100.00"), money("70.00"), money("30.00"),
                        money("10.00"), money("20.00"), money("40.00"), money("25.00"), money("15.00"), money("5.00"), 2L, money("4.00"))),
                List.of(new FinanceChartPoint("ago 2026", money("25.00"), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new FinanceChartPoint("ago 2026", money("150.00"), money("20.00"), money("5.00"))),
                List.of(new ProductProfitRow("Frijol", money("100.00"), money("4.00"), money("70.00"), money("30.00"), money("30.00")))
        );
        when(financeService.summary(anyString(), any(), any(), anyString())).thenReturn(summary);

        mockMvc.perform(get("/admin/finances"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Finanzas")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Costo vendido")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Frijol")));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
