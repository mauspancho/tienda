package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@NormalMode
public class WhatsappGroupPersistenceService {
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappGroupRepository groups;

    public WhatsappGroupPersistenceService(TenantWhatsappConnectionRepository connections,
                                           WhatsappGroupRepository groups) {
        this.connections = connections;
        this.groups = groups;
    }

    @Transactional
    public List<WhatsappGroup> applyCompleteSync(Long tenantId, Long connectionId,
                                                  List<WppConnectClient.RemoteGroup> remote) {
        TenantWhatsappConnection connection = connections.findByIdWithDetailsForUpdate(connectionId)
                .filter(value -> value.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new DomainException("La conexión de WhatsApp ya no pertenece a esta tienda."));
        LocalDateTime now = LocalDateTime.now();
        Map<String, WhatsappGroup> stored = new HashMap<>();
        List<WhatsappGroup> changed = new ArrayList<>();
        for (WhatsappGroup group : groups.findByTenantIdForUpdate(tenantId)) {
            group.setAvailable(false);
            stored.put(group.getWhatsappId(), group);
            changed.add(group);
        }
        for (WppConnectClient.RemoteGroup item : remote) {
            WhatsappGroup group = stored.get(item.id());
            if (group == null) {
                group = new WhatsappGroup();
                group.setTenant(connection.getTenant());
                group.setWhatsappId(item.id());
                changed.add(group);
            }
            group.setConnection(connection);
            group.setName(item.name());
            if (item.participants() != null) group.setParticipantCount(item.participants());
            group.setAvailable(true);
            group.setRefreshedAt(now);
        }
        groups.saveAll(changed);
        return groups.findByTenantIdAndAvailableTrueOrderByNameAsc(tenantId);
    }
}
