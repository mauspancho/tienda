package com.tienda.pos.whatsapp.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.WhatsappCryptoService;
import com.tienda.pos.whatsapp.WppConnectClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.Callable;

@Service
@NormalMode
public class AutomationIdempotencyService {
    private final PlatformAutomationIdempotencyRepository records;
    private final PlatformAutomationTokenRepository tokens;
    private final WhatsappCryptoService crypto;
    private final ObjectMapper mapper;
    public AutomationIdempotencyService(PlatformAutomationIdempotencyRepository records,
                                        PlatformAutomationTokenRepository tokens,
                                        WhatsappCryptoService crypto, ObjectMapper mapper) {
        this.records = records; this.tokens = tokens; this.crypto = crypto; this.mapper = mapper;
    }
    @Transactional
    public synchronized WppConnectClient.SendResult execute(AutomationPrincipal principal, Tenant tenant, String key,
                                                            String signature, Callable<WppConnectClient.SendResult> action) {
        if (key == null || key.isBlank()) throw new DomainException("Idempotency-Key es obligatoria para envíos de automatización.");
        if (key.length() > 200) throw new DomainException("Idempotency-Key no puede superar 200 caracteres.");
        String keyHash = crypto.hashToken(key.trim()); String requestHash = crypto.hashToken(signature);
        var existing = records.findByTokenIdAndTenantIdAndOperationNameAndKeyHash(
                principal.tokenId(), tenant.getId(), "ANNOUNCEMENT_SEND", keyHash);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) throw new DomainException("Idempotency-Key ya fue utilizada con una solicitud diferente.");
            try { return mapper.readValue(existing.get().getResponseJson(), WppConnectClient.SendResult.class); }
            catch (Exception ex) { throw new IllegalStateException("No fue posible recuperar la respuesta idempotente.", ex); }
        }
        try {
            WppConnectClient.SendResult result = action.call();
            PlatformAutomationIdempotency record = new PlatformAutomationIdempotency();
            record.setToken(tokens.getReferenceById(principal.tokenId())); record.setTenant(tenant);
            record.setOperationName("ANNOUNCEMENT_SEND"); record.setKeyHash(keyHash); record.setRequestHash(requestHash);
            record.setResponseJson(mapper.writeValueAsString(result)); records.save(record);
            return result;
        } catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
