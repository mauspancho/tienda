package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.category.Category;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.tenant.TenantRepository;
import com.tienda.pos.whatsapp.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappAutomationServiceTest {
    private TenantRepository tenants;
    private TenantWhatsappAnnouncementSettingsRepository settings;
    private WhatsappAnnouncementService announcements;
    private ProductRepository products;
    private WhatsappIntegrationService whatsapp;
    private WhatsappAutomationService service;
    private Tenant tenant;

    @BeforeEach void setUp() {
        tenants = mock(TenantRepository.class); settings = mock(TenantWhatsappAnnouncementSettingsRepository.class);
        announcements = mock(WhatsappAnnouncementService.class); products = mock(ProductRepository.class); whatsapp = mock(WhatsappIntegrationService.class);
        service = new WhatsappAutomationService(tenants, settings, announcements, products, whatsapp);
        tenant = new Tenant(); tenant.setId(1L); tenant.setCode("default"); tenant.setName("Default");
        when(tenants.findByCode("default")).thenReturn(Optional.of(tenant));
        when(announcements.view(tenant)).thenReturn(readyView(tenant));
    }

    @Test void promotionsAndCatalogUseTenantScopedQueriesAndDifferentSets() {
        List<Product> promotional = products(tenant, 5, true);
        List<Product> catalog = products(tenant, 20, false);
        when(products.findWhatsappPromotions(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(promotional));
        when(products.findWhatsappCatalog(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(catalog));
        assertThat(service.promotions("default", Pageable.ofSize(100)).items()).hasSize(5).allMatch(WhatsappAutomationService.ProductDto::promocionWhatsapp);
        assertThat(service.catalog("default", Pageable.ofSize(100)).items()).hasSize(20);
        verify(products).findWhatsappPromotions(eq(1L), any(Pageable.class));
        verify(products).findWhatsappCatalog(eq(1L), any(Pageable.class));
    }

    @Test void tenantProductsNeverUseAnotherTenantId() {
        when(products.findWhatsappCatalog(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.catalog("default", Pageable.ofSize(20));
        verify(products).findWhatsappCatalog(eq(1L), any(Pageable.class));
        verify(products, never()).findWhatsappCatalog(eq(2L), any(Pageable.class));
    }

    @Test void unselectedGroupIsRejectedBeforeWppConnect() {
        when(announcements.requireAuthorizedGroup(tenant, "foreign@g.us")).thenThrow(new DomainException("no autorizado"));
        assertThatThrownBy(() -> service.sendText(tenant, "foreign@g.us", "hola")).isInstanceOf(DomainException.class);
        verifyNoInteractions(whatsapp);
    }

    @Test void noEligibleStoresReturnsValidEmptyList() {
        when(settings.findEnabledWithTenantOrderByCode()).thenReturn(List.of());
        assertThat(service.eligibleStores()).isEmpty();
    }

    private WhatsappAnnouncementService.View readyView(Tenant value) {
        TenantWhatsappAnnouncementSettings configured = new TenantWhatsappAnnouncementSettings(); configured.setTenant(value); configured.setEnabled(true);
        WhatsappProvider provider = new WhatsappProvider(); provider.setId(2L); provider.setEnabled(true); provider.setStatus(ProviderStatus.AVAILABLE);
        TenantWhatsappConnection connection = new TenantWhatsappConnection(); connection.setId(3L); connection.setTenant(value); connection.setProvider(provider); connection.setStatus(WhatsappSessionStatus.CONNECTED);
        return new WhatsappAnnouncementService.View(value, configured, connection, WhatsappAnnouncementStatus.LISTO, 2, 2, 2);
    }
    private List<Product> products(Tenant owner, int count, boolean promotion) {
        Category category = new Category(); category.setId(9L); category.setName("Abarrotes"); category.setTenant(owner);
        List<Product> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Product p = new Product(); p.setId((long) i + 1); p.setTenant(owner); p.setCode("P" + i); p.setName("Producto " + i);
            p.setSalePrice(BigDecimal.TEN); p.setCurrentStock(BigDecimal.ONE); p.setPromocionWhatsapp(promotion); p.setCategory(category); result.add(p);
        }
        return result;
    }
}
