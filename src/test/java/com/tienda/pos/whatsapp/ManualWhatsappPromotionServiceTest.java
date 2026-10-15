package com.tienda.pos.whatsapp;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductImageService;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.settings.BusinessSettingsRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManualWhatsappPromotionServiceTest {
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageService images = mock(ProductImageService.class);
    private final WhatsappAnnouncementService announcements = mock(WhatsappAnnouncementService.class);
    private final WhatsappIntegrationService whatsapp = mock(WhatsappIntegrationService.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BusinessSettingsRepository settings = mock(BusinessSettingsRepository.class);
    private final ManualWhatsappPromotionPersistenceService persistence = mock(ManualWhatsappPromotionPersistenceService.class);
    private final ManualWhatsappPromotionService service = new ManualWhatsappPromotionService(currentTenant, products,
            images, announcements, whatsapp, users, settings, persistence);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendsExistingImageAndCustomMessageOnlyToSelectedTenantGroups() {
        Fixture fixture = fixture(1L, 10L, 100L, List.of(group(501L, "Grupo A", "11111@g.us", 41L),
                group(502L, "Grupo B", "22222@g.us", 41L)));
        when(whatsapp.sendFile(eq(fixture.tenant), anyString(), eq(true), eq("product.jpg"), any(byte[].class), eq("Oferta especial")))
                .thenReturn(new WppConnectClient.SendResult(true, "wamid-1", "accepted"));
        var pending = prepared(true, ManualPromotionStatus.PROCESSING, 0, 0,
                delivery(701L, "11111@g.us", "Grupo A", ManualPromotionDeliveryStatus.PENDING, null),
                delivery(702L, "22222@g.us", "Grupo B", ManualPromotionDeliveryStatus.PENDING, null));
        when(persistence.prepare(eq(1L), eq(10L), eq(100L), eq(41L), eq("requestkey123456789"),
                eq("Oferta especial"), eq(List.of(501L, 502L)))).thenReturn(pending);
        when(persistence.finish(1L, 900L)).thenReturn(prepared(false, ManualPromotionStatus.COMPLETED, 2, 0,
                delivery(701L, "11111@g.us", "Grupo A", ManualPromotionDeliveryStatus.ACCEPTED, null),
                delivery(702L, "22222@g.us", "Grupo B", ManualPromotionDeliveryStatus.ACCEPTED, null)));

        var result = service.send(10L, "Oferta especial", "requestkey123456789");

        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(whatsapp, times(2)).sendFile(eq(fixture.tenant), anyString(), eq(true), eq("product.jpg"),
                aryEq(new byte[]{1, 2, 3}), eq("Oferta especial"));
        verify(announcements).manualPromotionGroups(fixture.tenant);
    }

    @Test
    void recordsPartialResultsWhenOneGroupFails() {
        Fixture fixture = fixture(2L, 20L, 200L, List.of(group(601L, "Bien", "33333@g.us", 42L),
                group(602L, "Falla", "44444@g.us", 42L)));
        when(whatsapp.sendFile(eq(fixture.tenant), eq("33333@g.us"), eq(true), anyString(), any(), anyString()))
                .thenReturn(new WppConnectClient.SendResult(true, "ok", "accepted"));
        when(whatsapp.sendFile(eq(fixture.tenant), eq("44444@g.us"), eq(true), anyString(), any(), anyString()))
                .thenThrow(new DomainException("Proveedor no disponible"));
        when(persistence.prepare(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(prepared(true, ManualPromotionStatus.PROCESSING, 0, 0,
                        delivery(801L, "33333@g.us", "Bien", ManualPromotionDeliveryStatus.PENDING, null),
                        delivery(802L, "44444@g.us", "Falla", ManualPromotionDeliveryStatus.PENDING, null)));
        when(persistence.finish(2L, 900L)).thenReturn(prepared(false, ManualPromotionStatus.PARTIAL, 1, 1,
                delivery(801L, "33333@g.us", "Bien", ManualPromotionDeliveryStatus.ACCEPTED, null),
                delivery(802L, "44444@g.us", "Falla", ManualPromotionDeliveryStatus.FAILED, "Proveedor no disponible")));

        var result = service.send(20L, "Mensaje", "requestkey123456789");

        assertThat(result.accepted()).isOne();
        assertThat(result.failed()).isOne();
        verify(persistence).recordResult(eq(2L), eq(801L), any(), isNull());
        verify(persistence).recordResult(2L, 802L, null, "Proveedor no disponible");
    }

    @Test
    void rejectsProductsFromAnotherTenantAndProductsWithoutStockOrImage() {
        Tenant tenant = tenant(3L);
        when(currentTenant.get()).thenReturn(tenant);
        when(products.findDetailedByIdAndTenantId(99L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(99L, "Mensaje", "requestkey123456789"))
                .isInstanceOf(DomainException.class).hasMessageContaining("esta tienda");

        Product noStock = product(30L, tenant, false);
        when(products.findDetailedByIdAndTenantId(30L, 3L)).thenReturn(Optional.of(noStock));
        assertThatThrownBy(() -> service.send(30L, "Mensaje", "requestkey123456789"))
                .isInstanceOf(DomainException.class).hasMessageContaining("existencia");

        Product noImage = product(31L, tenant, true);
        noImage.setImageUrl("https://example.invalid/image.jpg");
        when(products.findDetailedByIdAndTenantId(31L, 3L)).thenReturn(Optional.of(noImage));
        when(images.isLocalImage(noImage.getImageUrl())).thenReturn(false);
        assertThatThrownBy(() -> service.send(31L, "Mensaje", "requestkey123456789"))
                .isInstanceOf(DomainException.class).hasMessageContaining("imagen local");
        verifyNoInteractions(whatsapp);
    }

    @Test
    void disconnectedWhatsappOrUnauthorizedGroupsPreventSending() {
        Fixture fixture = fixture(4L, 40L, 400L, List.of());
        when(announcements.manualPromotionGroups(fixture.tenant))
                .thenThrow(new DomainException("WhatsApp está desconectado"));

        assertThatThrownBy(() -> service.send(40L, "Mensaje", "requestkey123456789"))
                .isInstanceOf(DomainException.class).hasMessageContaining("desconectado");
        verifyNoInteractions(persistence, whatsapp);
    }

    @Test
    void previewShowsWhatsappWarningWithoutFailingProductForm() {
        Fixture fixture = fixture(7L, 70L, 700L, List.of());
        when(announcements.manualPromotionGroups(fixture.tenant))
                .thenThrow(new DomainException("WhatsApp está desconectado"));

        var preview = service.preview(70L);

        assertThat(preview.ready()).isFalse();
        assertThat(preview.warning()).isEqualTo("WhatsApp está desconectado");
        assertThat(preview.groupNames()).isEmpty();
        assertThat(preview.suggestedMessage()).contains("Producto 70", "$20.00 MXN");
    }

    @Test
    void previewSuspendsAmbientTransactionBeforeHandlingWhatsappAvailabilityErrors() throws Exception {
        Transactional transaction = ManualWhatsappPromotionService.class
                .getMethod("preview", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void groupRevokedOrMovedToAnotherTenantIsNotSent() {
        Fixture fixture = fixture(6L, 60L, 600L,
                List.of(group(951L, "Grupo revocado", "66666@g.us", 46L)));
        when(persistence.prepare(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(prepared(true, ManualPromotionStatus.PROCESSING, 0, 0,
                        delivery(1051L, "66666@g.us", "Grupo revocado", ManualPromotionDeliveryStatus.PENDING, null)));
        when(announcements.requireManualPromotionGroup(fixture.tenant, "66666@g.us"))
                .thenThrow(new DomainException("El grupo ya no está autorizado para promociones."));
        when(persistence.finish(6L, 900L)).thenReturn(prepared(false, ManualPromotionStatus.FAILED, 0, 1,
                delivery(1051L, "66666@g.us", "Grupo revocado", ManualPromotionDeliveryStatus.FAILED,
                        "El grupo ya no está autorizado para promociones.")));

        var result = service.send(60L, "Mensaje", "requestkey123456789");

        assertThat(result.failed()).isOne();
        verifyNoInteractions(whatsapp);
        verify(persistence).recordResult(6L, 1051L, null,
                "El grupo ya no está autorizado para promociones.");
    }

    @Test
    void duplicateRequestReturnsStoredResultWithoutSendingAgain() {
        fixture(5L, 50L, 500L, List.of(group(901L, "Grupo", "55555@g.us", 45L)));
        when(persistence.prepare(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(prepared(false, ManualPromotionStatus.COMPLETED, 1, 0,
                        delivery(1001L, "55555@g.us", "Grupo", ManualPromotionDeliveryStatus.ACCEPTED, null)));

        var result = service.send(50L, "Mensaje", "requestkey123456789");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.accepted()).isOne();
        verifyNoInteractions(whatsapp);
    }

    @Test
    void sendsImageAndCaptionOnlyToNormalizedIndividualNumberWithoutGroups() {
        Fixture fixture = fixture(8L, 80L, 800L, List.of());
        TenantWhatsappConnection connection = connection(48L, fixture.tenant);
        when(whatsapp.normalizeIndividualDestination("55 1234-5678")).thenReturn("525512345678");
        when(whatsapp.requireConnectedConnection(fixture.tenant)).thenReturn(connection);
        when(persistence.prepareNumber(8L, 80L, 800L, 48L, "requestkey123456789", "Oferta individual",
                "525512345678")).thenReturn(prepared(true, ManualPromotionStatus.PROCESSING, 0, 0,
                delivery(1080L, "525512345678", "+525512345678", ManualPromotionDeliveryStatus.PENDING, null)));
        when(whatsapp.sendFile(fixture.tenant, "525512345678", false, "product.jpg",
                new byte[]{1, 2, 3}, "Oferta individual"))
                .thenReturn(new WppConnectClient.SendResult(true, "message-1", "accepted"));
        when(persistence.finish(8L, 900L)).thenReturn(prepared(false, ManualPromotionStatus.COMPLETED, 1, 0,
                delivery(1080L, "525512345678", "+525512345678", ManualPromotionDeliveryStatus.ACCEPTED, null)));

        var result = service.send(80L, "Oferta individual", "requestkey123456789", true, "55 1234-5678");

        assertThat(result.accepted()).isOne();
        verifyNoInteractions(announcements);
        verify(whatsapp).sendFile(fixture.tenant, "525512345678", false, "product.jpg",
                new byte[]{1, 2, 3}, "Oferta individual");
    }

    @Test
    void individualModeRejectsInvalidNumberAndDisconnectedWhatsapp() {
        Fixture fixture = fixture(9L, 90L, 900L, List.of());
        when(whatsapp.normalizeIndividualDestination("grupo@g.us"))
                .thenThrow(new DomainException("Escribe un número individual de WhatsApp válido."));
        assertThatThrownBy(() -> service.send(90L, "Oferta", "requestkey123456789", true, "grupo@g.us"))
                .isInstanceOf(DomainException.class).hasMessageContaining("individual");

        when(whatsapp.normalizeIndividualDestination("+1 212 555 0100")).thenReturn("12125550100");
        when(whatsapp.requireConnectedConnection(fixture.tenant))
                .thenThrow(new DomainException("WhatsApp no está conectado."));
        assertThatThrownBy(() -> service.send(90L, "Oferta", "requestkey123456789", true, "+1 212 555 0100"))
                .isInstanceOf(DomainException.class).hasMessageContaining("no está conectado");
        verifyNoInteractions(persistence);
    }

    @Test
    void duplicateIndividualRequestDoesNotSendAgain() {
        Fixture fixture = fixture(10L, 100L, 1000L, List.of());
        when(whatsapp.normalizeIndividualDestination("5512345678")).thenReturn("525512345678");
        when(whatsapp.requireConnectedConnection(fixture.tenant)).thenReturn(connection(50L, fixture.tenant));
        when(persistence.prepareNumber(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(prepared(false, ManualPromotionStatus.COMPLETED, 1, 0,
                        delivery(1100L, "525512345678", "+525512345678",
                                ManualPromotionDeliveryStatus.ACCEPTED, null)));

        var result = service.send(100L, "Oferta", "requestkey123456789", true, "5512345678");

        assertThat(result.duplicate()).isTrue();
        verify(whatsapp, never()).sendFile(any(), anyString(), anyBoolean(), anyString(), any(), anyString());
    }

    @Test
    void resolvesEachOfThreeStoresIndependently() {
        for (long tenantId = 11; tenantId <= 13; tenantId++) {
            reset(currentTenant, products, images, announcements, whatsapp, users, persistence);
            Fixture fixture = fixture(tenantId, tenantId * 10, tenantId * 100,
                    List.of(group(tenantId * 1000, "Grupo " + tenantId, tenantId + "0000@g.us", tenantId + 40)));
            when(persistence.prepare(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyList()))
                    .thenReturn(prepared(false, ManualPromotionStatus.COMPLETED, 1, 0,
                            delivery(tenantId, tenantId + "0000@g.us", "Grupo " + tenantId,
                                    ManualPromotionDeliveryStatus.ACCEPTED, null)));
            service.send(fixture.product.getId(), "Mensaje " + tenantId, "requestkey123456789");
            verify(products).findDetailedByIdAndTenantId(fixture.product.getId(), tenantId);
            verify(announcements).manualPromotionGroups(fixture.tenant);
        }
    }

    private Fixture fixture(Long tenantId, Long productId, Long userId, List<WhatsappGroup> selectedGroups) {
        Tenant tenant = tenant(tenantId);
        Product product = product(productId, tenant, true);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.isActive()).thenReturn(true);
        when(user.getTenant()).thenReturn(tenant);
        when(user.hasRole("ROLE_ADMIN")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin" + tenantId,
                "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(currentTenant.get()).thenReturn(tenant);
        when(products.findDetailedByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(images.isLocalImage(product.getImageUrl())).thenReturn(true);
        when(images.readLocalImage(product.getImageUrl()))
                .thenReturn(new ProductImageService.ProductImagePayload("product.jpg", "image/jpeg", new byte[]{1, 2, 3}));
        when(users.findByUsernameWithTenant("admin" + tenantId)).thenReturn(Optional.of(user));
        when(whatsapp.isConnected(tenant)).thenReturn(true);
        when(announcements.manualPromotionGroups(tenant)).thenReturn(selectedGroups);
        for (WhatsappGroup group : selectedGroups) {
            when(announcements.requireManualPromotionGroup(tenant, group.getWhatsappId())).thenReturn(group);
        }
        return new Fixture(tenant, product);
    }

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setActive(true);
        tenant.setCode("tenant-" + id);
        tenant.setName("Tienda " + id);
        return tenant;
    }

    private Product product(Long id, Tenant tenant, boolean stock) {
        Product product = new Product();
        product.setId(id);
        product.setTenant(tenant);
        product.setName("Producto " + id);
        product.setActive(true);
        product.setCurrentStock(stock ? BigDecimal.TEN : BigDecimal.ZERO);
        product.setSalePrice(new BigDecimal("20.00"));
        product.setImageUrl("/uploads/products/product.jpg");
        return product;
    }

    private WhatsappGroup group(Long id, String name, String whatsappId, Long connectionId) {
        TenantWhatsappConnection connection = connection(connectionId, null);
        WhatsappGroup group = new WhatsappGroup();
        group.setId(id);
        group.setName(name);
        group.setWhatsappId(whatsappId);
        group.setConnection(connection);
        group.setAvailable(true);
        group.setSelectedForAnnouncements(true);
        return group;
    }

    private TenantWhatsappConnection connection(Long id, Tenant tenant) {
        TenantWhatsappConnection connection = new TenantWhatsappConnection();
        connection.setId(id);
        connection.setTenant(tenant);
        connection.setStatus(WhatsappSessionStatus.CONNECTED);
        return connection;
    }

    private ManualWhatsappPromotionPersistenceService.DeliveryTarget delivery(Long id, String whatsappId, String name,
                                                                               ManualPromotionDeliveryStatus status,
                                                                               String error) {
        return new ManualWhatsappPromotionPersistenceService.DeliveryTarget(id, whatsappId, name, status, error);
    }

    private ManualWhatsappPromotionPersistenceService.Prepared prepared(boolean created, ManualPromotionStatus status,
                                                                         int accepted, int failed,
                                                                         ManualWhatsappPromotionPersistenceService.DeliveryTarget... targets) {
        return new ManualWhatsappPromotionPersistenceService.Prepared(900L, created, status, accepted, failed, List.of(targets));
    }

    private record Fixture(Tenant tenant, Product product) { }
}
