package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class PlatformAutomationAuditService {
    private final PlatformAutomationAuditRepository audits;
    private final PlatformAutomationTokenRepository tokens;
    public PlatformAutomationAuditService(PlatformAutomationAuditRepository audits, PlatformAutomationTokenRepository tokens) {
        this.audits = audits; this.tokens = tokens;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tokenId, String action, String tenantCode, int status, String remoteAddress) {
        if (tokenId == null) return;
        PlatformAutomationAudit value = new PlatformAutomationAudit();
        value.setToken(tokens.getReferenceById(tokenId)); value.setAction(limit(action, 120));
        value.setTenantCode(limit(tenantCode, 60)); value.setHttpStatus(status); value.setRemoteAddress(limit(remoteAddress, 80));
        audits.save(value);
    }
    private String limit(String value, int size) { return value == null ? null : value.substring(0, Math.min(size, value.length())); }
}
