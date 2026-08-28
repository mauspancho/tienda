package com.tienda.pos.staticresources;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceInterfaceAssetTest {

    private static final Path ROOT = Path.of("");

    @Test
    void financeRoutesAreAdminOnlyAndLinkedFromMenus() throws Exception {
        String security = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/security/SecurityConfig.java"));
        String layout = Files.readString(ROOT.resolve("src/main/resources/templates/fragments/layout.html"));
        String controller = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/finance/FinanceController.java"));

        assertThat(security).contains("/admin/finances/**").contains("hasRole(\"ADMIN\")");
        assertThat(controller).contains("@RequestMapping(\"/admin/finances\")").contains("@PreAuthorize(\"hasRole('ADMIN')\")");
        assertThat(layout)
                .contains("Resumen financiero")
                .contains("@{/admin/finances}")
                .contains("@{/admin/finances/daily}")
                .contains("@{/admin/finances/capital}")
                .contains("mobile-finances-link");
    }

    @Test
    void purchasesCaptureFundingSourceForFinancialSeparation() throws Exception {
        String purchases = Files.readString(ROOT.resolve("src/main/resources/templates/purchases/index.html"));
        String form = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/purchase/PurchaseForm.java"));
        String service = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/purchase/PurchaseService.java"));

        assertThat(purchases)
                .contains("Origen del dinero")
                .contains("th:field=\"*{fundingSource}\"")
                .contains("source.label")
                .contains("p.fundingSource.label");
        assertThat(form).contains("PurchaseFundingSource.BUSINESS_CASH");
        assertThat(service).contains("purchase.setFundingSource(form.getFundingSource())");
    }

    @Test
    void financePagesExposeRequiredMetricsAndCsv() throws Exception {
        String index = Files.readString(ROOT.resolve("src/main/resources/templates/finances/index.html"));
        String daily = Files.readString(ROOT.resolve("src/main/resources/templates/finances/daily.html"));
        String capital = Files.readString(ROOT.resolve("src/main/resources/templates/finances/capital.html"));
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        assertThat(index)
                .contains("Costo vendido")
                .contains("Ganancia bruta")
                .contains("Utilidad neta")
                .contains("Capital dentro del negocio")
                .contains("Utilidad potencial")
                .contains("Flujo simplificado")
                .contains("Más rentables")
                .contains("Mayor facturación")
                .contains("data-finance-bars");
        assertThat(daily).contains("Exportar CSV").contains("Costo vendido").contains("Reinversión");
        assertThat(capital).contains("Nuevo movimiento").contains("m.type.label");
        assertThat(css).contains(".finance-filter").contains(".finance-bars").contains(".finance-list");
    }
}