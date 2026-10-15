package com.tienda.pos.platform;

import com.tienda.pos.auth.AuthController;
import com.tienda.pos.cash.CashService;
import com.tienda.pos.category.CategoryController;
import com.tienda.pos.externalproduct.ExternalProductService;
import com.tienda.pos.hardening.HardeningTestConfiguration;
import com.tienda.pos.inventory.InventoryController;
import com.tienda.pos.pos.PosController;
import com.tienda.pos.product.*;
import com.tienda.pos.sale.SaleController;
import com.tienda.pos.supplier.SupplierController;
import com.tienda.pos.user.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

@TestConfiguration(proxyBeanMethods = false)
@EnableMethodSecurity
@org.springframework.boot.context.properties.EnableConfigurationProperties({com.tienda.pos.catalog.CatalogImagesProperties.class,
        ProductImagesProperties.class, com.tienda.pos.whatsapp.WhatsappProperties.class})
@org.springframework.context.annotation.ComponentScan("com.tienda.pos.api.v1")
@Import({HardeningTestConfiguration.class, PlatformTenantController.class, PlatformTenantService.class,
        com.tienda.pos.backup.TenantBackupService.class, com.tienda.pos.backup.PlatformBackupController.class,
        TenantProvisioningService.class, UserService.class, UserController.class, ProductService.class,
        ProductController.class, BarcodeLabelService.class, InventoryController.class, SaleController.class,
        PosController.class, CashService.class, CategoryController.class, SupplierController.class, AuthController.class,
        com.tienda.pos.api.ProductApiController.class,
        com.tienda.pos.dashboard.DashboardController.class, com.tienda.pos.dashboard.DashboardService.class,
        com.tienda.pos.credit.CreditService.class, com.tienda.pos.credit.CreditController.class,
        com.tienda.pos.customer.CustomerService.class, com.tienda.pos.category.CategoryService.class,
        com.tienda.pos.supplier.SupplierService.class, com.tienda.pos.expense.ExpenseService.class,
        com.tienda.pos.finance.FinanceService.class, com.tienda.pos.report.ReportService.class,
        com.tienda.pos.settings.SettingsService.class, com.tienda.pos.purchase.PurchaseService.class,
        com.tienda.pos.purchase.PurchaseController.class, com.tienda.pos.catalog.CatalogImageService.class,
        com.tienda.pos.whatsapp.WhatsappCryptoService.class,
        com.tienda.pos.whatsapp.WhatsappAnnouncementService.class,
        com.tienda.pos.whatsapp.automation.AutomationApiSecurityConfig.class,
        com.tienda.pos.whatsapp.automation.WhatsappAutomationApiController.class,
        com.tienda.pos.whatsapp.automation.WhatsappAutomationService.class,
        com.tienda.pos.whatsapp.automation.PlatformAutomationTokenService.class,
        com.tienda.pos.whatsapp.automation.PlatformAutomationTokenUsageService.class,
        com.tienda.pos.whatsapp.automation.PlatformAutomationAuditService.class,
        com.tienda.pos.whatsapp.automation.AutomationRateLimiter.class,
        com.tienda.pos.whatsapp.automation.AutomationIdempotencyService.class})
public class PlatformTestConfiguration {
    // Only filesystem/external network collaborators are mocked; persistence, transactions, auth and MVC are real.
    @Bean ProductImageService productImageService() { return mock(ProductImageService.class); }
    @Bean ExternalProductService externalProductService() { return mock(ExternalProductService.class); }
    @Bean com.tienda.pos.whatsapp.WhatsappIntegrationService whatsappIntegrationService() {
        return mock(com.tienda.pos.whatsapp.WhatsappIntegrationService.class);
    }
    @Bean com.tienda.pos.whatsapp.ManualWhatsappPromotionService manualWhatsappPromotionService() {
        var service = mock(com.tienda.pos.whatsapp.ManualWhatsappPromotionService.class);
        when(service.preview(anyLong())).thenReturn(new com.tienda.pos.whatsapp.ManualWhatsappPromotionService.Preview(
                false, false, "WhatsApp no está configurado.", "WhatsApp no está configurado.",
                "", java.util.List.of(), 0));
        return service;
    }
}
