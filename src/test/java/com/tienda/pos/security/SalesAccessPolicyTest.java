package com.tienda.pos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SalesAccessPolicyTest {

    private static final Path ROOT = Path.of("");

    @Test
    void cashiersOnlyListTheirOwnSalesWithinTenant() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleController.java"));
        String repository = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleRepository.java"));

        assertThat(controller)
                .contains("CurrentUser.hasRole(\"ROLE_ADMIN\")")
                .contains("findByTenantIdOrderBySaleDateDesc(tenantId, pageRequest)")
                .contains("findByTenantIdAndCashierUsernameOrderBySaleDateDesc(tenantId, CurrentUser.username(), pageRequest)");
        assertThat(repository)
                .contains("findByTenantIdAndCashierUsernameOrderBySaleDateDesc")
                .contains("findByTenantIdAndFolioAndCashierUsername");
    }

    @Test
    void cashiersCannotOpenOtherUsersSaleDetailsOrTickets() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleController.java"));

        assertThat(controller)
                .contains("private Sale visibleSale(String folio)")
                .contains("findByTenantIdAndFolio(tenantId, folio)")
                .contains("findByTenantIdAndFolioAndCashierUsername(tenantId, folio, CurrentUser.username())")
                .contains("Venta no disponible para este usuario.");
    }

    @Test
    void dashboardSalesDataIsFilteredForCashiersWithinTenant() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/dashboard/DashboardController.java"));
        String service = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/dashboard/DashboardService.java"));
        String repository = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleRepository.java"));

        assertThat(controller)
                .contains("findByTenantIdAndCashierUsernameOrderBySaleDateDesc(tenantId, username")
                .contains("dashboardService.today(username, admin)")
                .contains("dashboardService.today(CurrentUser.username(), CurrentUser.hasRole(\"ROLE_ADMIN\"))")
                .contains("dashboardService.profitAnalysis(date, period, CurrentUser.username(), CurrentUser.hasRole(\"ROLE_ADMIN\"))");
        assertThat(service)
                .contains("totalSalesByCashier(tenantId")
                .contains("grossProfitByCashier(tenantId")
                .contains("soldUnitsByCashier(tenantId")
                .contains("dailySalesSinceByCashier(tenantId")
                .contains("dailyGrossProfitBetweenByCashier(currentTenant.id()")
                .contains("topProductsByCashier(tenantId");
        assertThat(repository)
                .contains("countByTenantIdAndCashierUsernameAndSaleDateBetweenAndStatus")
                .contains("dailySalesSinceByCashier")
                .contains("dailyGrossProfitBetweenByCashier")
                .contains("topProductsByCashier");
    }
}