package com.tienda.pos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SalesAccessPolicyTest {

    private static final Path ROOT = Path.of("");

    @Test
    void cashiersOnlyListTheirOwnSales() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleController.java"));
        String repository = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleRepository.java"));

        assertThat(controller)
                .contains("CurrentUser.hasRole(\"ROLE_ADMIN\")")
                .contains("findAllByOrderBySaleDateDesc(pageRequest)")
                .contains("findByCashierUsernameOrderBySaleDateDesc(CurrentUser.username(), pageRequest)");
        assertThat(repository)
                .contains("findByCashierUsernameOrderBySaleDateDesc")
                .contains("findByFolioAndCashierUsername");
    }

    @Test
    void cashiersCannotOpenOtherUsersSaleDetailsOrTickets() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleController.java"));

        assertThat(controller)
                .contains("private Sale visibleSale(String folio)")
                .contains("findByFolio(folio)")
                .contains("findByFolioAndCashierUsername(folio, CurrentUser.username())")
                .contains("Venta no disponible para este usuario.");
    }

    @Test
    void dashboardSalesDataIsFilteredForCashiers() throws Exception {
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/dashboard/DashboardController.java"));
        String service = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/dashboard/DashboardService.java"));
        String repository = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/sale/SaleRepository.java"));

        assertThat(controller)
                .contains("findByCashierUsernameOrderBySaleDateDesc(username")
                .contains("dashboardService.today(username, admin)")
                .contains("dashboardService.today(CurrentUser.username(), CurrentUser.hasRole(\"ROLE_ADMIN\"))");
        assertThat(service)
                .contains("totalSalesByCashier")
                .contains("grossProfitByCashier")
                .contains("soldUnitsByCashier")
                .contains("dailySalesSinceByCashier")
                .contains("topProductsByCashier");
        assertThat(repository)
                .contains("countByCashierUsernameAndSaleDateBetweenAndStatus")
                .contains("dailySalesSinceByCashier")
                .contains("topProductsByCashier");
    }
}