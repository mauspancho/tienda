package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@NormalMode
public class WhatsappGroupSyncService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappGroupSyncService.class);

    public enum Status { IDLE, QUEUED, RUNNING, COMPLETED, FAILED }
    public record Snapshot(Status status, boolean running, int groupCount, LocalDateTime startedAt,
                           LocalDateTime completedAt, LocalDateTime lastSuccessfulAt, String error) { }

    private record State(Long tenantId, Status status, int groupCount, LocalDateTime startedAt,
                         LocalDateTime completedAt, LocalDateTime lastSuccessfulAt, String error) {
        boolean running() { return status == Status.QUEUED || status == Status.RUNNING; }
    }

    private final CurrentTenant currentTenant;
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappGroupRepository groups;
    private final WhatsappIntegrationService integration;
    private final TaskExecutor executor;
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public WhatsappGroupSyncService(CurrentTenant currentTenant,
                                    TenantWhatsappConnectionRepository connections,
                                    WhatsappGroupRepository groups,
                                    WhatsappIntegrationService integration,
                                    @Qualifier("whatsappGroupSyncExecutor") TaskExecutor executor) {
        this.currentTenant = currentTenant;
        this.connections = connections;
        this.groups = groups;
        this.integration = integration;
        this.executor = executor;
    }

    public Snapshot startCurrent() {
        Long tenantId = currentTenant.id();
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenantId)
                .orElseThrow(() -> new DomainException("WhatsApp no está configurado."));
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED) {
            throw new DomainException("Conecta WhatsApp para consultar sus grupos.");
        }
        LocalDateTime now = LocalDateTime.now();
        AtomicBoolean scheduled = new AtomicBoolean();
        State state = states.compute(connection.getId(), (id, existing) -> {
            if (existing != null && existing.running()) return existing;
            scheduled.set(true);
            return new State(tenantId, Status.QUEUED, currentGroupCount(tenantId), now, null,
                    latestSuccessfulAt(tenantId), null);
        });
        if (scheduled.get()) {
            try {
                executor.execute(() -> synchronize(connection.getId(), tenantId));
            } catch (RuntimeException ex) {
                state = failed(connection.getId(), tenantId, now,
                        "No fue posible iniciar la sincronización en segundo plano.");
            }
        }
        return snapshot(states.getOrDefault(connection.getId(), state));
    }

    public Snapshot current() {
        Long tenantId = currentTenant.id();
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenantId).orElse(null);
        if (connection == null) return new Snapshot(Status.IDLE, false, 0, null, null, null, null);
        State state = states.get(connection.getId());
        if (state == null || !state.tenantId().equals(tenantId)) {
            return new Snapshot(Status.IDLE, false, currentGroupCount(tenantId), null, null,
                    latestSuccessfulAt(tenantId), null);
        }
        return snapshot(state);
    }

    private void synchronize(Long connectionId, Long tenantId) {
        State queued = states.get(connectionId);
        if (queued == null || !queued.tenantId().equals(tenantId)) return;
        states.put(connectionId, new State(tenantId, Status.RUNNING, queued.groupCount(), queued.startedAt(),
                null, queued.lastSuccessfulAt(), null));
        try {
            TenantWhatsappConnection connection = connections.findByIdWithDetails(connectionId)
                    .filter(value -> value.getTenant().getId().equals(tenantId))
                    .orElseThrow(() -> new DomainException("La conexión de WhatsApp ya no está disponible."));
            int count = integration.refreshGroups(connection).size();
            LocalDateTime completed = LocalDateTime.now();
            states.put(connectionId, new State(tenantId, Status.COMPLETED, count, queued.startedAt(),
                    completed, completed, null));
            log.info("WhatsApp group sync completed tenant={} connection={} groups={}", tenantId, connectionId, count);
        } catch (Exception ex) {
            failed(connectionId, tenantId, queued.startedAt(), safeError(ex));
            log.warn("WhatsApp group sync failed tenant={} connection={} reason={}", tenantId, connectionId,
                    safeError(ex));
        }
    }

    private State failed(Long connectionId, Long tenantId, LocalDateTime startedAt, String error) {
        State value = new State(tenantId, Status.FAILED, currentGroupCount(tenantId), startedAt,
                LocalDateTime.now(), latestSuccessfulAt(tenantId), error);
        states.put(connectionId, value);
        return value;
    }

    private int currentGroupCount(Long tenantId) {
        return Math.toIntExact(groups.countByTenantIdAndAvailableTrue(tenantId));
    }

    private LocalDateTime latestSuccessfulAt(Long tenantId) {
        return groups.findLatestRefreshedAt(tenantId).orElse(null);
    }

    private String safeError(Exception ex) {
        String value = ex instanceof DomainException || ex instanceof WppConnectException
                ? ex.getMessage() : "No fue posible completar la sincronización de grupos.";
        if (value == null || value.isBlank()) value = "No fue posible completar la sincronización de grupos.";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private Snapshot snapshot(State value) {
        return new Snapshot(value.status(), value.running(), value.groupCount(), value.startedAt(),
                value.completedAt(), value.lastSuccessfulAt(), value.error());
    }
}
