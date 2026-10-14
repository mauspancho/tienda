package com.tienda.pos.whatsapp.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.WhatsappCryptoService;
import com.tienda.pos.whatsapp.WppConnectClient;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomationIdempotencyServiceTest {
    @Test void repeatedConfirmedRequestReturnsStoredResponseWithoutSendingAgain() throws Exception {
        PlatformAutomationIdempotencyRepository records = mock(PlatformAutomationIdempotencyRepository.class);
        PlatformAutomationTokenRepository tokens = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        when(crypto.hashToken("same-key")).thenReturn("key-hash"); when(crypto.hashToken("signature")).thenReturn("request-hash");
        WppConnectClient.SendResult sent = new WppConnectClient.SendResult(true, "message-1", "sent");
        PlatformAutomationIdempotency stored = new PlatformAutomationIdempotency(); stored.setRequestHash("request-hash");
        stored.setResponseJson(new ObjectMapper().writeValueAsString(sent));
        when(records.findByTokenIdAndTenantIdAndOperationNameAndKeyHash(7L, 1L, "ANNOUNCEMENT_SEND", "key-hash"))
                .thenReturn(Optional.of(stored));
        AutomationIdempotencyService service = new AutomationIdempotencyService(records, tokens, crypto, new ObjectMapper());
        Tenant tenant = new Tenant(); tenant.setId(1L); AtomicInteger calls = new AtomicInteger();
        var result = service.execute(new AutomationPrincipal(7L, Set.of(AutomationScope.ANNOUNCEMENTS_SEND)), tenant,
                "same-key", "signature", () -> { calls.incrementAndGet(); return sent; });
        assertThat(result.messageId()).isEqualTo("message-1"); assertThat(calls).hasValue(0);
    }

    @Test void repeatedKeyWithDifferentRequestIsRejected() {
        PlatformAutomationIdempotencyRepository records = mock(PlatformAutomationIdempotencyRepository.class);
        PlatformAutomationTokenRepository tokens = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        when(crypto.hashToken("same-key")).thenReturn("key-hash"); when(crypto.hashToken("different")).thenReturn("other-hash");
        PlatformAutomationIdempotency stored = new PlatformAutomationIdempotency(); stored.setRequestHash("original-hash");
        when(records.findByTokenIdAndTenantIdAndOperationNameAndKeyHash(7L, 1L, "ANNOUNCEMENT_SEND", "key-hash"))
                .thenReturn(Optional.of(stored));
        AutomationIdempotencyService service = new AutomationIdempotencyService(records, tokens, crypto, new ObjectMapper());
        Tenant tenant = new Tenant(); tenant.setId(1L);
        assertThatThrownBy(() -> service.execute(new AutomationPrincipal(7L, Set.of()), tenant, "same-key", "different",
                () -> new WppConnectClient.SendResult(true, "x", "x"))).isInstanceOf(DomainException.class);
    }
}
