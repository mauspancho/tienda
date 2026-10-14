package com.tienda.pos.whatsapp;

import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsappGroupRefreshTest {
    @Test void refreshPreservesAnnouncementSelection() {
        Fixture f = fixture();
        when(f.client.groups(any(), anyString(), anyString())).thenReturn(List.of(new WppConnectClient.RemoteGroup("100@g.us", "Nuevo nombre", 8)));
        when(f.groups.findByTenantId(1L)).thenReturn(List.of(f.group));
        when(f.groups.findByTenantIdAndAvailableTrueOrderByNameAsc(1L)).thenReturn(List.of(f.group));
        f.service.refreshGroups(f.connection);
        assertThat(f.group.isSelectedForAnnouncements()).isTrue();
        assertThat(f.group.isAvailable()).isTrue();
        assertThat(f.group.getName()).isEqualTo("Nuevo nombre");
    }

    @Test void providerFailureDoesNotMarkStoredGroupsUnavailable() {
        Fixture f = fixture();
        when(f.client.groups(any(), anyString(), anyString())).thenThrow(new WppConnectException(WppConnectException.Kind.REMOTE_ERROR, "fallo"));
        assertThatThrownBy(() -> f.service.refreshGroups(f.connection)).isInstanceOf(WppConnectException.class);
        assertThat(f.group.isAvailable()).isTrue();
        assertThat(f.group.isSelectedForAnnouncements()).isTrue();
        verify(f.groups, never()).findByTenantId(anyLong());
    }

    private Fixture fixture() {
        CurrentTenant current = mock(CurrentTenant.class); WhatsappProviderRepository providers = mock(WhatsappProviderRepository.class);
        TenantWhatsappConnectionRepository connections = mock(TenantWhatsappConnectionRepository.class); WhatsappGroupRepository groups = mock(WhatsappGroupRepository.class);
        TenantIntegrationTokenRepository tokens = mock(TenantIntegrationTokenRepository.class); WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        WppConnectClient client = mock(WppConnectClient.class); WhatsappAnnouncementService announcements = mock(WhatsappAnnouncementService.class);
        Tenant tenant = new Tenant(); tenant.setId(1L); tenant.setCode("default"); tenant.setName("Default");
        WhatsappProvider provider = new WhatsappProvider(); provider.setId(2L); provider.setEnabled(true); provider.setStatus(ProviderStatus.AVAILABLE);
        TenantWhatsappConnection connection = new TenantWhatsappConnection(); connection.setId(3L); connection.setTenant(tenant); connection.setProvider(provider);
        connection.setSessionName("tp_1_default"); connection.setEncryptedSessionToken("encrypted"); connection.setStatus(WhatsappSessionStatus.CONNECTED);
        WhatsappGroup group = new WhatsappGroup(); group.setId(4L); group.setTenant(tenant); group.setConnection(connection); group.setWhatsappId("100@g.us");
        group.setName("Anterior"); group.setAvailable(true); group.setSelectedForAnnouncements(true); group.setRefreshedAt(LocalDateTime.now());
        when(crypto.decrypt("encrypted")).thenReturn("token");
        WhatsappIntegrationService service = new WhatsappIntegrationService(current, providers, connections, groups, tokens, crypto, client, new WhatsappProperties(), announcements);
        return new Fixture(service, client, groups, connection, group);
    }
    private record Fixture(WhatsappIntegrationService service, WppConnectClient client, WhatsappGroupRepository groups,
                           TenantWhatsappConnection connection, WhatsappGroup group) { }
}
