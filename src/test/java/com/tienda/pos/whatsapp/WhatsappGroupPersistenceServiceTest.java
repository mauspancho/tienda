package com.tienda.pos.whatsapp;

import com.tienda.pos.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class WhatsappGroupPersistenceServiceTest {
    @Test
    void completeSyncPreservesSelectionsAndOnlyThenUpdatesAvailability() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        TenantWhatsappConnection connection = new TenantWhatsappConnection();
        connection.setId(10L);
        connection.setTenant(tenant);
        WhatsappGroup selected = group(1L, tenant, connection, "one@g.us", true, true);
        WhatsappGroup disappeared = group(2L, tenant, connection, "old@g.us", true, false);
        TenantWhatsappConnectionRepository connections = mock(TenantWhatsappConnectionRepository.class);
        WhatsappGroupRepository groups = mock(WhatsappGroupRepository.class);
        when(connections.findByIdWithDetailsForUpdate(10L)).thenReturn(Optional.of(connection));
        when(groups.findByTenantIdForUpdate(1L)).thenReturn(List.of(selected, disappeared));
        when(groups.findByTenantIdAndAvailableTrueOrderByNameAsc(1L)).thenReturn(List.of(selected));
        WhatsappGroupPersistenceService service = new WhatsappGroupPersistenceService(connections, groups);

        service.applyCompleteSync(1L, 10L, List.of(
                new WppConnectClient.RemoteGroup("one@g.us", "Nombre nuevo", null),
                new WppConnectClient.RemoteGroup("new@g.us", "Grupo nuevo", null)));

        assertThat(selected.isSelectedForAnnouncements()).isTrue();
        assertThat(selected.isAvailable()).isTrue();
        assertThat(selected.getName()).isEqualTo("Nombre nuevo");
        assertThat(disappeared.isAvailable()).isFalse();
        verify(groups).saveAll(anyList());
    }

    private WhatsappGroup group(Long id, Tenant tenant, TenantWhatsappConnection connection, String whatsappId,
                                boolean available, boolean selected) {
        WhatsappGroup group = new WhatsappGroup();
        group.setId(id);
        group.setTenant(tenant);
        group.setConnection(connection);
        group.setWhatsappId(whatsappId);
        group.setName(whatsappId);
        group.setAvailable(available);
        group.setSelectedForAnnouncements(selected);
        group.setRefreshedAt(LocalDateTime.now());
        return group;
    }
}
