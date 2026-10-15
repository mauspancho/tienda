package com.tienda.pos.whatsapp;

import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsappGroupSyncServiceTest {
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final TenantWhatsappConnectionRepository connections = mock(TenantWhatsappConnectionRepository.class);
    private final WhatsappGroupRepository groups = mock(WhatsappGroupRepository.class);
    private final WhatsappIntegrationService integration = mock(WhatsappIntegrationService.class);

    @Test
    void completesFiveHundredGroupsInBackground() {
        TenantWhatsappConnection connection = connection(1L, 11L);
        when(currentTenant.id()).thenReturn(1L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(connections.findByIdWithDetails(11L)).thenReturn(Optional.of(connection));
        when(groups.countByTenantIdAndAvailableTrue(1L)).thenReturn(4L, 500L);
        when(groups.findLatestRefreshedAt(1L)).thenReturn(Optional.empty()).thenReturn(Optional.of(LocalDateTime.now()));
        when(integration.refreshGroups(connection)).thenReturn(remoteGroups(500));
        WhatsappGroupSyncService service = service(Runnable::run);

        var result = service.startCurrent();

        assertThat(result.status()).isEqualTo(WhatsappGroupSyncService.Status.COMPLETED);
        assertThat(result.groupCount()).isEqualTo(500);
        verify(integration).refreshGroups(connection);
    }

    @Test
    void coalescesTwoRequestsForTheSameConnection() {
        TenantWhatsappConnection connection = connection(1L, 11L);
        when(currentTenant.id()).thenReturn(1L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(groups.countByTenantIdAndAvailableTrue(1L)).thenReturn(7L);
        when(groups.findLatestRefreshedAt(1L)).thenReturn(Optional.<LocalDateTime>empty());
        CapturingExecutor executor = new CapturingExecutor();
        WhatsappGroupSyncService service = service(executor);

        var first = service.startCurrent();
        var second = service.startCurrent();

        assertThat(first.running()).isTrue();
        assertThat(second.running()).isTrue();
        assertThat(executor.tasks).hasSize(1);
    }

    @Test
    void schedulesDifferentTenantsIndependently() {
        TenantWhatsappConnection first = connection(1L, 11L);
        TenantWhatsappConnection second = connection(2L, 22L);
        when(currentTenant.id()).thenReturn(1L, 2L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(first));
        when(connections.findByTenantIdWithDetails(2L)).thenReturn(Optional.of(second));
        when(groups.countByTenantIdAndAvailableTrue(anyLong())).thenReturn(0L);
        when(groups.findLatestRefreshedAt(anyLong())).thenReturn(Optional.<LocalDateTime>empty());
        CapturingExecutor executor = new CapturingExecutor();
        WhatsappGroupSyncService service = service(executor);

        service.startCurrent();
        service.startCurrent();

        assertThat(executor.tasks).hasSize(2);
    }

    @Test
    void failureKeepsCachedCountAndReportsClearError() {
        TenantWhatsappConnection connection = connection(1L, 11L);
        when(currentTenant.id()).thenReturn(1L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(connections.findByIdWithDetails(11L)).thenReturn(Optional.of(connection));
        when(groups.countByTenantIdAndAvailableTrue(1L)).thenReturn(185L);
        when(groups.findLatestRefreshedAt(1L)).thenReturn(Optional.of(LocalDateTime.now()));
        when(integration.refreshGroups(connection)).thenThrow(new WppConnectException(
                WppConnectException.Kind.TIMEOUT, "WPPConnect agotó el tiempo de espera."));
        WhatsappGroupSyncService service = service(Runnable::run);

        var result = service.startCurrent();

        assertThat(result.status()).isEqualTo(WhatsappGroupSyncService.Status.FAILED);
        assertThat(result.groupCount()).isEqualTo(185);
        assertThat(result.error()).contains("tiempo de espera");
    }

    @Test
    void aNewSynchronizationCanRecoverAfterAnIncompleteAttempt() {
        TenantWhatsappConnection connection = connection(1L, 11L);
        when(currentTenant.id()).thenReturn(1L);
        when(connections.findByTenantIdWithDetails(1L)).thenReturn(Optional.of(connection));
        when(connections.findByIdWithDetails(11L)).thenReturn(Optional.of(connection));
        when(groups.countByTenantIdAndAvailableTrue(1L)).thenReturn(20L, 20L, 150L);
        when(groups.findLatestRefreshedAt(1L)).thenReturn(Optional.empty()).thenReturn(Optional.of(LocalDateTime.now()));
        when(integration.refreshGroups(connection))
                .thenThrow(new WppConnectException(WppConnectException.Kind.INCOMPATIBLE, "Página repetida"))
                .thenReturn(remoteGroups(150));
        WhatsappGroupSyncService service = service(Runnable::run);

        assertThat(service.startCurrent().status()).isEqualTo(WhatsappGroupSyncService.Status.FAILED);
        assertThat(service.startCurrent().status()).isEqualTo(WhatsappGroupSyncService.Status.COMPLETED);
    }

    private WhatsappGroupSyncService service(TaskExecutor executor) {
        return new WhatsappGroupSyncService(currentTenant, connections, groups, integration, executor);
    }

    private TenantWhatsappConnection connection(Long tenantId, Long connectionId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        TenantWhatsappConnection connection = new TenantWhatsappConnection();
        connection.setId(connectionId);
        connection.setTenant(tenant);
        connection.setStatus(WhatsappSessionStatus.CONNECTED);
        return connection;
    }

    private List<WhatsappGroup> remoteGroups(int count) {
        List<WhatsappGroup> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(new WhatsappGroup());
        return result;
    }

    private static class CapturingExecutor implements TaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
    }
}
