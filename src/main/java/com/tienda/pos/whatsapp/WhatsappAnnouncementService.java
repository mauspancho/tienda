package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@NormalMode
public class WhatsappAnnouncementService {
    public record View(Tenant tenant, TenantWhatsappAnnouncementSettings settings,
                       TenantWhatsappConnection connection, WhatsappAnnouncementStatus status,
                       long availableGroups, long selectedGroups, long selectedAvailableGroups) {
        public boolean ready() { return status == WhatsappAnnouncementStatus.LISTO; }
    }

    private final CurrentTenant currentTenant;
    private final TenantWhatsappAnnouncementSettingsRepository settings;
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappGroupRepository groups;
    private final WhatsappProperties properties;

    public WhatsappAnnouncementService(CurrentTenant currentTenant,
                                       TenantWhatsappAnnouncementSettingsRepository settings,
                                       TenantWhatsappConnectionRepository connections,
                                       WhatsappGroupRepository groups, WhatsappProperties properties) {
        this.currentTenant = currentTenant;
        this.settings = settings;
        this.connections = connections;
        this.groups = groups;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public View current() { return view(currentTenant.get()); }

    @Transactional(readOnly = true)
    public List<WhatsappGroup> currentGroups() { return groups.findByTenantIdOrderByNameAsc(currentTenant.id()); }

    @Transactional(readOnly = true)
    public View view(Tenant tenant) {
        TenantWhatsappAnnouncementSettings configured = settings.findByTenantId(tenant.getId()).orElse(null);
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenant.getId()).orElse(null);
        long available = groups.countByTenantIdAndAvailableTrue(tenant.getId());
        long selected = groups.countByTenantIdAndSelectedForAnnouncementsTrue(tenant.getId());
        long selectedAvailable = groups.countByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrue(tenant.getId());
        return new View(tenant, configured, connection,
                status(tenant, configured, connection, selected, selectedAvailable), available, selected, selectedAvailable);
    }

    @Transactional
    public View saveCurrentSelections(Collection<Long> requestedIds) {
        Tenant tenant = currentTenant.get();
        TenantWhatsappConnection connection = requireConnected(tenant);
        Set<Long> ids = requestedIds == null ? Set.of() : new HashSet<>(requestedIds);
        List<WhatsappGroup> stored = groups.findByTenantIdForUpdate(tenant.getId());
        Set<Long> owned = new HashSet<>();
        for (WhatsappGroup group : stored) {
            if (Objects.equals(group.getConnection().getId(), connection.getId()) && group.isAvailable()) owned.add(group.getId());
        }
        if (!owned.containsAll(ids)) throw new DomainException("Uno o más grupos no pertenecen a esta tienda o a su conexión actual.");
        TenantWhatsappAnnouncementSettings configured = settings(tenant);
        if (!identityMatches(configured, connection)) {
            stored.forEach(group -> group.setSelectedForAnnouncements(false));
            configured.setEnabled(false);
        }
        for (WhatsappGroup group : stored) {
            boolean sameConnection = Objects.equals(group.getConnection().getId(), connection.getId());
            if (sameConnection && group.isAvailable()) group.setSelectedForAnnouncements(ids.contains(group.getId()));
        }
        confirmIdentity(configured, connection);
        return view(tenant);
    }

    @Transactional
    public View setCurrentEnabled(boolean enabled) {
        Tenant tenant = currentTenant.get();
        TenantWhatsappAnnouncementSettings configured = settings(tenant);
        if (enabled) {
            TenantWhatsappConnection connection = requireConnected(tenant);
            if (!identityMatches(configured, connection)) {
                throw new DomainException("Actualiza y guarda nuevamente los grupos para confirmar la cuenta de WhatsApp actual.");
            }
        }
        configured.setEnabled(enabled);
        return view(tenant);
    }

    @Transactional
    public void invalidateIfIdentityChanged(TenantWhatsappConnection connection) {
        settings.findByTenantId(connection.getTenant().getId()).ifPresent(configured -> {
            if (hasConfirmedIdentity(configured) && !identityMatches(configured, connection)) {
                invalidate(configured, connection.getTenant().getId());
            }
        });
    }

    @Transactional
    public void invalidateForDisassociation(Long tenantId) {
        settings.findByTenantId(tenantId).ifPresent(configured -> invalidate(configured, tenantId));
    }

    @Transactional(readOnly = true)
    public List<WhatsappGroup> selectedAvailable(Tenant tenant) {
        View view = view(tenant);
        if (!view.ready()) throw new DomainException(message(view.status()));
        return groups.findByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrueOrderByNameAsc(tenant.getId());
    }

    @Transactional(readOnly = true)
    public List<WhatsappGroup> manualPromotionGroups(Tenant tenant) {
        TenantWhatsappConnection connection = requireManualPromotionConnection(tenant);
        List<WhatsappGroup> selected = groups
                .findByTenantIdAndConnectionIdAndSelectedForAnnouncementsTrueAndAvailableTrueOrderByNameAsc(
                        tenant.getId(), connection.getId());
        if (selected.isEmpty()) {
            throw new DomainException("Selecciona al menos un grupo desde Configuración de WhatsApp.");
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public WhatsappGroup requireManualPromotionGroup(Tenant tenant, String whatsappId) {
        TenantWhatsappConnection connection = requireManualPromotionConnection(tenant);
        WhatsappGroup group = groups.findByTenantIdAndWhatsappId(tenant.getId(), whatsappId)
                .orElseThrow(() -> new DomainException("El grupo no pertenece a esta tienda."));
        if (!group.isAvailable() || !group.isSelectedForAnnouncements()
                || !Objects.equals(group.getConnection().getId(), connection.getId())) {
            throw new DomainException("El grupo ya no está autorizado para promociones.");
        }
        return group;
    }

    @Transactional(readOnly = true)
    public WhatsappGroup requireAuthorizedGroup(Tenant tenant, String whatsappId) {
        if (whatsappId == null || whatsappId.isBlank()) throw new DomainException("El grupo de destino es obligatorio.");
        View view = view(tenant);
        if (!view.ready()) throw new DomainException(message(view.status()));
        WhatsappGroup group = groups.findByTenantIdAndWhatsappId(tenant.getId(), whatsappId.trim())
                .orElseThrow(() -> new DomainException("El grupo no pertenece a esta tienda."));
        if (!group.isSelectedForAnnouncements() || !group.isAvailable()) {
            throw new DomainException("El grupo no está autorizado para anuncios automáticos.");
        }
        if (!Objects.equals(group.getConnection().getId(), view.connection().getId())) {
            throw new DomainException("El grupo no corresponde a la conexión actual.");
        }
        return group;
    }

    public boolean identityMatches(TenantWhatsappAnnouncementSettings configured, TenantWhatsappConnection connection) {
        return configured != null && connection != null && connection.getPhoneNumber() != null
                && Objects.equals(configured.getConfirmedConnectionId(), connection.getId())
                && Objects.equals(configured.getConfirmedProviderId(), connection.getProvider().getId())
                && Objects.equals(configured.getConfirmedPhoneNumber(), connection.getPhoneNumber());
    }

    private WhatsappAnnouncementStatus status(Tenant tenant, TenantWhatsappAnnouncementSettings configured,
                                              TenantWhatsappConnection connection, long selected, long selectedAvailable) {
        if (!tenant.isActive()) return WhatsappAnnouncementStatus.TIENDA_SUSPENDIDA;
        if (configured == null || !configured.isEnabled()) return WhatsappAnnouncementStatus.DESHABILITADO;
        if (connection == null || !identityMatches(configured, connection)) return WhatsappAnnouncementStatus.SIN_SESION;
        if (selected == 0 || selectedAvailable == 0) return WhatsappAnnouncementStatus.SIN_GRUPOS;
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED || !fresh(connection.getLastCheckedAt())) return WhatsappAnnouncementStatus.DESCONECTADO;
        if (!connection.getProvider().isEnabled() || connection.getProvider().getStatus() != ProviderStatus.AVAILABLE) {
            return WhatsappAnnouncementStatus.PROVEEDOR_NO_DISPONIBLE;
        }
        return WhatsappAnnouncementStatus.LISTO;
    }

    private boolean fresh(LocalDateTime checkedAt) {
        long milliseconds = Math.max(60_000L, properties.getMonitorDelayMs() * 2L);
        return checkedAt != null && !checkedAt.isBefore(LocalDateTime.now().minusNanos(milliseconds * 1_000_000L));
    }

    private TenantWhatsappConnection requireConnected(Tenant tenant) {
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenant.getId())
                .orElseThrow(() -> new DomainException("Vincula una cuenta de WhatsApp antes de configurar anuncios."));
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED || connection.getPhoneNumber() == null) {
            throw new DomainException("WhatsApp debe estar conectado para confirmar los grupos publicitarios.");
        }
        return connection;
    }

    private TenantWhatsappConnection requireManualPromotionConnection(Tenant tenant) {
        if (!tenant.isActive()) throw new DomainException("La tienda está suspendida.");
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenant.getId())
                .orElseThrow(() -> new DomainException("Vincula una cuenta desde Configuración de WhatsApp."));
        TenantWhatsappAnnouncementSettings configured = settings.findByTenantId(tenant.getId())
                .orElseThrow(() -> new DomainException("Selecciona y confirma los grupos desde Configuración de WhatsApp."));
        if (!identityMatches(configured, connection)) {
            throw new DomainException("La cuenta actual debe confirmarse nuevamente desde Configuración de WhatsApp.");
        }
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED || !fresh(connection.getLastCheckedAt())) {
            throw new DomainException("WhatsApp está desconectado o su estado no es reciente.");
        }
        if (!connection.getProvider().isEnabled() || connection.getProvider().getStatus() != ProviderStatus.AVAILABLE) {
            throw new DomainException("El proveedor WPPConnect no está disponible.");
        }
        return connection;
    }

    private TenantWhatsappAnnouncementSettings settings(Tenant tenant) {
        return settings.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantWhatsappAnnouncementSettings value = new TenantWhatsappAnnouncementSettings();
            value.setTenant(tenant);
            return settings.save(value);
        });
    }

    private void confirmIdentity(TenantWhatsappAnnouncementSettings configured, TenantWhatsappConnection connection) {
        configured.setConfirmedConnectionId(connection.getId());
        configured.setConfirmedProviderId(connection.getProvider().getId());
        configured.setConfirmedPhoneNumber(connection.getPhoneNumber());
        configured.setConfirmedAt(LocalDateTime.now());
    }

    private boolean hasConfirmedIdentity(TenantWhatsappAnnouncementSettings configured) {
        return configured.getConfirmedConnectionId() != null || configured.getConfirmedProviderId() != null
                || configured.getConfirmedPhoneNumber() != null;
    }

    private void invalidate(TenantWhatsappAnnouncementSettings configured, Long tenantId) {
        configured.setEnabled(false);
        configured.setConfirmedConnectionId(null);
        configured.setConfirmedProviderId(null);
        configured.setConfirmedPhoneNumber(null);
        configured.setConfirmedAt(null);
        groups.findByTenantIdForUpdate(tenantId).forEach(group -> group.setSelectedForAnnouncements(false));
    }

    private String message(WhatsappAnnouncementStatus status) {
        return switch (status) {
            case DESHABILITADO -> "Los anuncios automáticos están deshabilitados para esta tienda.";
            case SIN_GRUPOS -> "Selecciona al menos un grupo disponible para anuncios.";
            case SIN_SESION -> "La cuenta de WhatsApp actual no ha sido confirmada para anuncios.";
            case DESCONECTADO -> "WhatsApp está desconectado o su estado no es reciente.";
            case PROVEEDOR_NO_DISPONIBLE -> "El proveedor WPPConnect no está disponible.";
            case TIENDA_SUSPENDIDA -> "La tienda está suspendida.";
            case LISTO -> "La tienda está lista.";
        };
    }
}
