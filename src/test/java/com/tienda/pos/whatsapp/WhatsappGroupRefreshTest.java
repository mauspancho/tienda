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
        when(f.persistence.applyCompleteSync(eq(1L), eq(3L), anyList())).thenAnswer(invocation -> {
            f.group.setName("Nuevo nombre");
            f.group.setParticipantCount(8);
            return List.of(f.group);
        });
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
        verifyNoInteractions(f.persistence);
    }

    @Test void statusWithoutPhonePreservesPreviouslyKnownIdentity() {
        Fixture f = fixture();
        f.connection.setPhoneNumber("5215512345678");
        f.connection.getProvider().setStatus(ProviderStatus.TIMEOUT);
        when(f.client.status(any(), anyString(), anyString()))
                .thenReturn(new WppConnectClient.SessionSnapshot(WhatsappSessionStatus.CONNECTED, null));

        f.service.refreshStatus(f.connection);

        assertThat(f.connection.getPhoneNumber()).isEqualTo("5215512345678");
        assertThat(f.connection.getProvider().getStatus()).isEqualTo(ProviderStatus.TIMEOUT);
        verify(f.announcements).invalidateIfIdentityChanged(f.connection);
    }

    @Test void normalizesMexicanAndInternationalIndividualNumbers() {
        Fixture f = fixture();
        assertThat(f.service.normalizeIndividualDestination("55 1234-5678")).isEqualTo("525512345678");
        assertThat(f.service.normalizeIndividualDestination("+1 (212) 555-0100")).isEqualTo("12125550100");
        assertThat(f.service.normalizeIndividualDestination("+52 1 55 1234 5678")).isEqualTo("525512345678");
    }

    @Test void rejectsInvalidAndGroupDestinations() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.service.normalizeIndividualDestination("55123"))
                .isInstanceOf(com.tienda.pos.exception.DomainException.class).hasMessageContaining("código de país");
        assertThatThrownBy(() -> f.service.normalizeIndividualDestination("120363000@g.us"))
                .isInstanceOf(com.tienda.pos.exception.DomainException.class).hasMessageContaining("individual");
        assertThatThrownBy(() -> f.service.normalizeIndividualDestination("+52 call-me"))
                .isInstanceOf(com.tienda.pos.exception.DomainException.class).hasMessageContaining("solo puede contener");
    }

    @Test void recentStatusIsReusedWithoutCallingWppConnectAgain() {
        Fixture f = fixture();
        f.connection.setLastCheckedAt(LocalDateTime.now());

        TenantWhatsappConnection result = f.service.currentStatusCached();

        assertThat(result).isSameAs(f.connection);
        verify(f.client, never()).status(any(), anyString(), anyString());
    }

    private Fixture fixture() {
        CurrentTenant current = mock(CurrentTenant.class); WhatsappProviderRepository providers = mock(WhatsappProviderRepository.class);
        TenantWhatsappConnectionRepository connections = mock(TenantWhatsappConnectionRepository.class); WhatsappGroupRepository groups = mock(WhatsappGroupRepository.class);
        TenantIntegrationTokenRepository tokens = mock(TenantIntegrationTokenRepository.class); WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        WppConnectClient client = mock(WppConnectClient.class); WhatsappAnnouncementService announcements = mock(WhatsappAnnouncementService.class);
        WhatsappGroupPersistenceService persistence = mock(WhatsappGroupPersistenceService.class);
        Tenant tenant = new Tenant(); tenant.setId(1L); tenant.setCode("default"); tenant.setName("Default");
        WhatsappProvider provider = new WhatsappProvider(); provider.setId(2L); provider.setEnabled(true); provider.setStatus(ProviderStatus.AVAILABLE);
        TenantWhatsappConnection connection = new TenantWhatsappConnection(); connection.setId(3L); connection.setTenant(tenant); connection.setProvider(provider);
        connection.setSessionName("tp_1_default"); connection.setEncryptedSessionToken("encrypted"); connection.setStatus(WhatsappSessionStatus.CONNECTED);
        WhatsappGroup group = new WhatsappGroup(); group.setId(4L); group.setTenant(tenant); group.setConnection(connection); group.setWhatsappId("100@g.us");
        group.setName("Anterior"); group.setAvailable(true); group.setSelectedForAnnouncements(true); group.setRefreshedAt(LocalDateTime.now());
        when(crypto.decrypt("encrypted")).thenReturn("token");
        when(current.id()).thenReturn(1L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(connections.findByIdWithDetailsForUpdate(3L)).thenReturn(Optional.of(connection));
        when(connections.findByIdWithDetails(3L)).thenReturn(Optional.of(connection));
        WhatsappIntegrationService service = new WhatsappIntegrationService(current, providers, connections, groups, tokens, crypto,
                client, new WhatsappProperties(), announcements, persistence);
        return new Fixture(service, client, groups, announcements, persistence, connection, group);
    }
    private record Fixture(WhatsappIntegrationService service, WppConnectClient client, WhatsappGroupRepository groups,
                           WhatsappAnnouncementService announcements,
                           WhatsappGroupPersistenceService persistence,
                           TenantWhatsappConnection connection, WhatsappGroup group) { }
}
