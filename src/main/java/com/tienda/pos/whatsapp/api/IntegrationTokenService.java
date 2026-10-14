package com.tienda.pos.whatsapp.api;

import com.tienda.pos.whatsapp.*;
import com.tienda.pos.common.NormalMode;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@NormalMode
public class IntegrationTokenService {
    private final TenantIntegrationTokenRepository tokens;
    private final WhatsappCryptoService crypto;
    public IntegrationTokenService(TenantIntegrationTokenRepository tokens, WhatsappCryptoService crypto) { this.tokens = tokens; this.crypto = crypto; }

    @Transactional
    public IntegrationPrincipal authenticate(String raw) {
        if (raw == null || !raw.startsWith("tip_") || raw.length() < 32) throw new BadCredentialsException("Credencial inválida.");
        TenantIntegrationToken token = tokens.findByTokenHashAndActiveTrue(crypto.hashToken(raw))
                .orElseThrow(() -> new BadCredentialsException("Credencial inválida o revocada."));
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) throw new BadCredentialsException("Credencial vencida.");
        if (!token.getTenant().isActive()) throw new BadCredentialsException("La tienda está suspendida.");
        token.setLastUsedAt(LocalDateTime.now());
        Set<IntegrationScope> scopes = Arrays.stream(token.getScopes().split(",")).map(String::trim)
                .map(IntegrationScope::valueOf).collect(Collectors.toUnmodifiableSet());
        return new IntegrationPrincipal(token.getId(), token.getTenant(), scopes);
    }
}
