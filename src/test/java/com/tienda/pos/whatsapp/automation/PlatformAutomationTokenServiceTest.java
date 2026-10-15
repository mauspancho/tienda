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
        PlatformAutomationTokenUsageService usage = mock(PlatformAutomationTokenUsageService.class);
        when(crypto.hashToken(anyString())).thenReturn("hash");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformAutomationTokenService service = new PlatformAutomationTokenService(repository, crypto, usage);
        var generated = service.generate("n8n", Set.of(AutomationScope.ANNOUNCEMENTS_READ));
        assertThat(generated.value()).startsWith("tap_");
        assertThat(generated.token().getTokenHash()).isEqualTo("hash").doesNotContain(generated.value());
    }

    @Test void revokedOrIndividualTenantTokenCannotAuthenticate() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class); when(crypto.hashToken(anyString())).thenReturn("hash");
        PlatformAutomationTokenService service = new PlatformAutomationTokenService(repository, crypto, mock(PlatformAutomationTokenUsageService.class));
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
        assertThatThrownBy(() -> new PlatformAutomationTokenService(repository, crypto, mock(PlatformAutomationTokenUsageService.class))
                .authenticate("tap_expired_token_12345678901234567890"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void authenticationDoesNotMutateManagedTokenAndRecordsUsageSeparately() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        PlatformAutomationTokenUsageService usage = mock(PlatformAutomationTokenUsageService.class);
        PlatformAutomationToken token = new PlatformAutomationToken();
        token.setId(7L); token.setScopes("ANNOUNCEMENTS_READ,ANNOUNCEMENTS_SEND");
        LocalDateTime previousUse = LocalDateTime.now().minusDays(1); token.setLastUsedAt(previousUse);
        when(crypto.hashToken(anyString())).thenReturn("hash");
        when(repository.findByTokenHashAndActiveTrue("hash")).thenReturn(Optional.of(token));

        AutomationPrincipal principal = new PlatformAutomationTokenService(repository, crypto, usage)
                .authenticate("tap_valid_token_123456789012345678901");

        assertThat(principal.tokenId()).isEqualTo(7L);
        assertThat(principal.scopes()).containsExactlyInAnyOrder(AutomationScope.ANNOUNCEMENTS_READ, AutomationScope.ANNOUNCEMENTS_SEND);
        assertThat(token.getLastUsedAt()).isEqualTo(previousUse);
        verify(usage).record(eq(7L), any(LocalDateTime.class));
        verify(repository, never()).save(token);
    }

    @Test void recentUsageDoesNotIssueAnotherDatabaseUpdate() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        WhatsappCryptoService crypto = mock(WhatsappCryptoService.class);
        PlatformAutomationTokenUsageService usage = mock(PlatformAutomationTokenUsageService.class);
        PlatformAutomationToken token = new PlatformAutomationToken();
        token.setId(8L); token.setScopes("ANNOUNCEMENTS_READ"); token.setLastUsedAt(LocalDateTime.now());
        when(crypto.hashToken(anyString())).thenReturn("hash");
        when(repository.findByTokenHashAndActiveTrue("hash")).thenReturn(Optional.of(token));

        new PlatformAutomationTokenService(repository, crypto, usage)
                .authenticate("tap_valid_token_123456789012345678901");

        verifyNoInteractions(usage);
    }
}
