package com.tienda.pos.whatsapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.whatsapp.TenantIntegrationTokenRepository;
import com.tienda.pos.whatsapp.WhatsappCryptoService;
import com.tienda.pos.whatsapp.WppConnectClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.Callable;

@Service
@NormalMode
public class IntegrationIdempotencyService {
    private final IntegrationIdempotencyRepository records;
    private final TenantIntegrationTokenRepository tokens;
    private final WhatsappCryptoService crypto;
    private final ObjectMapper mapper;
    public IntegrationIdempotencyService(IntegrationIdempotencyRepository records, TenantIntegrationTokenRepository tokens,
                                         WhatsappCryptoService crypto, ObjectMapper mapper) {
        this.records = records; this.tokens = tokens; this.crypto = crypto; this.mapper = mapper;
    }
    @Transactional
    public synchronized WppConnectClient.SendResult execute(IntegrationPrincipal principal, String key, String requestSignature,
                                                            Callable<WppConnectClient.SendResult> action) {
        if (key == null || key.isBlank()) return call(action);
        if (key.length() > 200) throw new DomainException("Idempotency-Key no puede superar 200 caracteres.");
        String keyHash = crypto.hashToken(key.trim()); String requestHash = crypto.hashToken(requestSignature);
        var existing = records.findByTenantIdAndTokenIdAndOperationNameAndKeyHash(principal.tenant().getId(), principal.tokenId(), "WHATSAPP_SEND_TEXT", keyHash);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) throw new DomainException("Idempotency-Key ya fue utilizada con una solicitud diferente.");
            try { return mapper.readValue(existing.get().getResponseJson(), WppConnectClient.SendResult.class); }
            catch (Exception ex) { throw new IllegalStateException("No fue posible recuperar la respuesta idempotente.", ex); }
        }
        WppConnectClient.SendResult result = call(action);
        try {
            IntegrationIdempotencyRecord record = new IntegrationIdempotencyRecord(); record.setTenant(principal.tenant());
            record.setToken(tokens.getReferenceById(principal.tokenId())); record.setOperationName("WHATSAPP_SEND_TEXT");
            record.setKeyHash(keyHash); record.setRequestHash(requestHash); record.setResponseJson(mapper.writeValueAsString(result)); records.save(record);
            return result;
        } catch (Exception ex) { throw new IllegalStateException("No fue posible registrar la solicitud idempotente.", ex); }
    }
    private WppConnectClient.SendResult call(Callable<WppConnectClient.SendResult> action) {
        try { return action.call(); } catch (RuntimeException ex) { throw ex; } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
