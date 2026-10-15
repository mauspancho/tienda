package com.tienda.pos.whatsapp;

import org.slf4j.Logger;
import com.tienda.pos.common.NormalMode;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@NormalMode
public class WhatsappSessionMonitor {
    private static final Logger log = LoggerFactory.getLogger(WhatsappSessionMonitor.class);
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappIntegrationService service;
    public WhatsappSessionMonitor(TenantWhatsappConnectionRepository connections, WhatsappIntegrationService service) {
        this.connections = connections; this.service = service;
    }
    @Scheduled(fixedDelayString = "${tienda.whatsapp.monitor-delay-ms:300000}")
    public void checkConnections() {
        for (TenantWhatsappConnection connection : connections.findAllWithDetails()) {
            if (!connection.getProvider().isEnabled() || !connection.getTenant().isActive()) continue;
            try { service.refreshStatusFromMonitor(connection); }
            catch (RuntimeException ex) { log.warn("No fue posible comprobar la sesión de WhatsApp id={}: {}", connection.getId(), ex.getMessage()); }
        }
    }
}
