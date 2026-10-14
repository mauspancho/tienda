package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.whatsapp.WhatsappCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAutomationTokenServiceTest {
    @Test void generatedTokenIsStoredOnlyAsHashAndShownOnce() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        when(crypto.hashToken(anyString())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformAutomationTokenService service = new PlatformAutomationTokenService(repository, crypto);
        var generated = service.generate("n8n", Set.of(AutomationScope.ANNOUNCEMENTS_READ));
        assertThat(generated.value()).startsWith("tap_");
        assertThat(generated.token().getTokenHash()).isEqualTo("hash").doesNotContain(generated.value());
    }

    @Test void revokedOrIndividualTenantTokenCannotAuthenticate() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class); when(crypto.hashToken(anyString())).thenReturn("hash");
        PlatformAutomationTokenService service = new PlatformAutomationTokenService(repository, crypto);
        assertThatThrownBy(() -> service.authenticate("tip_individual_tenant_token_123456789"))
                .isInstanceOf(BadCredentialsException.class);
        when(repository.findByTokenHashAndActiveTrue("hash")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("tap_revoked_token_12345678901234567890"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void expiredTokenCannotAuthenticate() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class); when(crypto.hashToken(anyString())).thenReturn("hash");
        PlatformAutomationToken token = new PlatformAutomationToken(); token.setScopes("ANNOUNCEMENTS_READ"); token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHashAndActiveTrue("hash")).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> new PlatformAutomationTokenService(repository, crypto).authenticate("tap_expired_token_12345678901234567890"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
