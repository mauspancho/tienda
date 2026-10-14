package com.tienda.pos.whatsapp.api;

import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import java.util.Base64;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegrationTokenServiceTest {
    @Test void revokedTokenIsRejected() {
        TenantIntegrationTokenRepository repository = mock(TenantIntegrationTokenRepository.class);
        IntegrationTokenService service = new IntegrationTokenService(repository, crypto());
        assertThatThrownBy(() -> service.authenticate("tip_abcdefghijklmnopqrstuvwxyz123456789"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void suspendedTenantIsRejectedImmediately() {
        String raw = "tip_abcdefghijklmnopqrstuvwxyz123456789";
        Tenant tenant = new Tenant(); tenant.setId(1L); tenant.setCode("one"); tenant.setName("One"); tenant.setActive(false);
        TenantIntegrationToken token = new TenantIntegrationToken(); token.setTenant(tenant); token.setScopes("SEND");
        TenantIntegrationTokenRepository repository = mock(TenantIntegrationTokenRepository.class);
        when(repository.findByTokenHashAndActiveTrue(crypto().hashToken(raw))).thenReturn(Optional.of(token));
        IntegrationTokenService service = new IntegrationTokenService(repository, crypto());
        assertThatThrownBy(() -> service.authenticate(raw)).isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("suspendida");
    }

    @Test void validTokenResolvesOnlyItsTenantAndScopes() {
        String raw = "tip_abcdefghijklmnopqrstuvwxyz123456789";
        Tenant tenant = new Tenant(); tenant.setId(7L); tenant.setCode("seven"); tenant.setName("Seven");
        TenantIntegrationToken token = new TenantIntegrationToken(); token.setId(4L); token.setTenant(tenant); token.setScopes("GROUPS,STATUS");
        WhatsappCryptoService crypto = crypto();
        TenantIntegrationTokenRepository repository = mock(TenantIntegrationTokenRepository.class);
        when(repository.findByTokenHashAndActiveTrue(crypto.hashToken(raw))).thenReturn(Optional.of(token));
        IntegrationPrincipal principal = new IntegrationTokenService(repository, crypto).authenticate(raw);
        assertThat(principal.tenant()).isSameAs(tenant);
        assertThat(principal.scopes()).containsExactlyInAnyOrder(IntegrationScope.GROUPS, IntegrationScope.STATUS);
        assertThat(token.getLastUsedAt()).isNotNull();
    }

    private WhatsappCryptoService crypto() {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new WhatsappCryptoService(properties);
    }
}
