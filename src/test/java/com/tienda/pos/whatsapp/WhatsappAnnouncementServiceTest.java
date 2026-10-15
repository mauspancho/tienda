package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsappAnnouncementServiceTest {
    private CurrentTenant currentTenant;
    private TenantWhatsappAnnouncementSettingsRepository settings;
    private TenantWhatsappConnectionRepository connections;
    private WhatsappGroupRepository groups;
    private WhatsappAnnouncementService service;
    private Tenant tenant;
    private TenantWhatsappConnection connection;
    private TenantWhatsappAnnouncementSettings configured;

    @BeforeEach void setUp() {
        currentTenant = mock(CurrentTenant.class); settings = mock(TenantWhatsappAnnouncementSettingsRepository.class);
        connections = mock(TenantWhatsappConnectionRepository.class); groups = mock(WhatsappGroupRepository.class);
        WhatsappProperties properties = new WhatsappProperties(); properties.setMonitorDelayMs(300_000);
        service = new WhatsappAnnouncementService(currentTenant, settings, connections, groups, properties);
        tenant = tenant(1L, "default"); connection = connection(10L, tenant, "5215511111111");
        configured = settings(tenant, connection, true);
        when(currentTenant.get()).thenReturn(tenant);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(settings.findByTenantId(1L)).thenReturn(Optional.of(configured));
    }

    @Test void savesAvailableSelectionsAndPreservesUnavailableSelectedGroup() {
        WhatsappGroup available = group(100L, tenant, connection, true, false);
        WhatsappGroup disappeared = group(101L, tenant, connection, false, true);
        when(groups.findByTenantIdForUpdate(1L)).thenReturn(List.of(available, disappeared));
        service.saveCurrentSelections(List.of(100L));
        assertThat(available.isSelectedForAnnouncements()).isTrue();
        assertThat(disappeared.isSelectedForAnnouncements()).isTrue();
    }

    @Test void rejectsForeignGroupAndChangesNothing() {
        WhatsappGroup owned = group(100L, tenant, connection, true, true);
        when(groups.findByTenantIdForUpdate(1L)).thenReturn(List.of(owned));
        assertThatThrownBy(() -> service.saveCurrentSelections(List.of(999L)))
                .isInstanceOf(DomainException.class).hasMessageContaining("no pertenecen");
        assertThat(owned.isSelectedForAnnouncements()).isTrue();
    }

    @Test void disablingKeepsSelectedGroups() {
        service.setCurrentEnabled(false);
        assertThat(configured.isEnabled()).isFalse();
        verify(groups, never()).findByTenantIdForUpdate(anyLong());
    }

    @Test void disconnectedStoreIsNotReadyAndKeepsSelections() {
        connection.setStatus(WhatsappSessionStatus.DISCONNECTED);
        when(groups.countByTenantIdAndSelectedForAnnouncementsTrue(1L)).thenReturn(3L);
        when(groups.countByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrue(1L)).thenReturn(3L);
        var view = service.view(tenant);
        assertThat(view.status()).isEqualTo(WhatsappAnnouncementStatus.DESCONECTADO);
        assertThat(view.selectedGroups()).isEqualTo(3);
    }

    @Test void changingPhoneInvalidatesAuthorizationAndSelections() {
        WhatsappGroup selected = group(100L, tenant, connection, true, true);
        when(groups.findByTenantIdForUpdate(1L)).thenReturn(List.of(selected));
        connection.setPhoneNumber("5215522222222");
        service.invalidateIfIdentityChanged(connection);
        assertThat(configured.isEnabled()).isFalse();
        assertThat(configured.getConfirmedPhoneNumber()).isNull();
        assertThat(selected.isSelectedForAnnouncements()).isFalse();
    }

    @Test void selectedUnavailableGroupIsNotReturned() {
        when(groups.countByTenantIdAndSelectedForAnnouncementsTrue(1L)).thenReturn(1L);
        when(groups.countByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrue(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.selectedAvailable(tenant)).isInstanceOf(DomainException.class);
        verify(groups, never()).findByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrueOrderByNameAsc(anyLong());
    }

    private Tenant tenant(Long id, String code) { Tenant value = new Tenant(); value.setId(id); value.setCode(code); value.setName(code); return value; }
    private TenantWhatsappConnection connection(Long id, Tenant owner, String phone) {
        WhatsappProvider provider = new WhatsappProvider(); provider.setId(5L); provider.setEnabled(true); provider.setStatus(ProviderStatus.AVAILABLE);
        TenantWhatsappConnection value = new TenantWhatsappConnection(); value.setId(id); value.setTenant(owner); value.setProvider(provider);
        value.setPhoneNumber(phone); value.setStatus(WhatsappSessionStatus.CONNECTED); value.setLastCheckedAt(LocalDateTime.now()); return value;
    }
    private TenantWhatsappAnnouncementSettings settings(Tenant owner, TenantWhatsappConnection current, boolean enabled) {
        TenantWhatsappAnnouncementSettings value = new TenantWhatsappAnnouncementSettings(); value.setTenant(owner); value.setEnabled(enabled);
        value.setConfirmedConnectionId(current.getId()); value.setConfirmedProviderId(current.getProvider().getId());
        value.setConfirmedPhoneNumber(current.getPhoneNumber()); value.setConfirmedAt(LocalDateTime.now()); return value;
    }
    private WhatsappGroup group(Long id, Tenant owner, TenantWhatsappConnection current, boolean available, boolean selected) {
        WhatsappGroup value = new WhatsappGroup(); value.setId(id); value.setTenant(owner); value.setConnection(current);
        value.setWhatsappId(id + "@g.us"); value.setName("Grupo " + id); value.setAvailable(available);
        value.setSelectedForAnnouncements(selected); value.setRefreshedAt(LocalDateTime.now()); return value;
    }
}
