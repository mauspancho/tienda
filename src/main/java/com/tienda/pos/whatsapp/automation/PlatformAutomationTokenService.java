package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.whatsapp.WhatsappCryptoService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@NormalMode
public class PlatformAutomationTokenService {
    public record GeneratedToken(String value, PlatformAutomationToken token) { }
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PlatformAutomationTokenRepository tokens;
    private final WhatsappCryptoService crypto;
    private final PlatformAutomationTokenUsageService usage;

    public PlatformAutomationTokenService(PlatformAutomationTokenRepository tokens, WhatsappCryptoService crypto,
                                          PlatformAutomationTokenUsageService usage) {
        this.tokens = tokens; this.crypto = crypto; this.usage = usage;
    }

    @Transactional
    public GeneratedToken generate(String name, Set<AutomationScope> scopes) {
        if (scopes == null || scopes.isEmpty()) throw new DomainException("Selecciona al menos un permiso de automatización.");
        String cleanName = name == null || name.isBlank() ? "n8n" : name.trim();
        if (cleanName.length() > 120) throw new DomainException("El nombre de la credencial no puede superar 120 caracteres.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String value = "tap_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PlatformAutomationToken token = new PlatformAutomationToken();
        token.setName(cleanName);
        token.setTokenPrefix(value.substring(0, 12));
        token.setTokenHash(crypto.hashToken(value));
        token.setScopes(scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        return new GeneratedToken(value, tokens.save(token));
    }

    public AutomationPrincipal authenticate(String raw) {
        if (raw == null || !raw.startsWith("tap_") || raw.length() < 32) throw new BadCredentialsException("Credencial de automatización inválida.");
        PlatformAutomationToken token = tokens.findByTokenHashAndActiveTrue(crypto.hashToken(raw))
                .orElseThrow(() -> new BadCredentialsException("Credencial de automatización inválida o revocada."));
        if (token.getRevokedAt() != null) {
            throw new BadCredentialsException("Credencial de automatización inválida o revocada.");
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Credencial de automatización vencida.");
        }
        Set<AutomationScope> scopes = Arrays.stream(token.getScopes().split(",")).map(String::trim)
                .map(AutomationScope::valueOf).collect(Collectors.toUnmodifiableSet());
        LocalDateTime usedAt = LocalDateTime.now();
        if (token.getLastUsedAt() == null
                || token.getLastUsedAt().isBefore(usedAt.minus(PlatformAutomationTokenUsageService.UPDATE_INTERVAL))) {
            usage.record(token.getId(), usedAt);
        }
        return new AutomationPrincipal(token.getId(), scopes);
    }

    @Transactional
    public void revoke(Long id) {
        PlatformAutomationToken token = tokens.findById(id).orElseThrow(() -> new DomainException("Credencial no encontrada."));
        token.setActive(false); token.setRevokedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<PlatformAutomationToken> list() { return tokens.findAllByOrderByCreatedAtDesc(); }
}
