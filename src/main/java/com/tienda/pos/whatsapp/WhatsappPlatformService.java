package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@NormalMode
public class WhatsappPlatformService {
    public record ProviderView(WhatsappProvider provider, long sessions, long connected, long disconnected) { }
    public record PageData(List<ProviderView> providers, List<TenantWhatsappConnection> connections,
                           List<WppConnectClient.RemoteSession> remoteSessions) { }
    private final WhatsappProviderRepository providers;
    private final TenantWhatsappConnectionRepository connections;
    private final TenantRepository tenants;
    private final WhatsappCryptoService crypto;
    private final ProviderUrlValidator urls;
    private final WppConnectClient client;

    public WhatsappPlatformService(WhatsappProviderRepository providers, TenantWhatsappConnectionRepository connections,
                                   TenantRepository tenants, WhatsappCryptoService crypto, ProviderUrlValidator urls,
                                   WppConnectClient client) {
        this.providers = providers; this.connections = connections; this.tenants = tenants;
        this.crypto = crypto; this.urls = urls; this.client = client;
    }

    @Transactional(readOnly = true)
    public PageData page() {
        List<ProviderView> views = providers.findAllByOrderByNameAsc().stream().map(p -> new ProviderView(p,
                connections.countByProviderId(p.getId()),
                connections.countByProviderIdAndStatus(p.getId(), WhatsappSessionStatus.CONNECTED),
                connections.countByProviderId(p.getId()) - connections.countByProviderIdAndStatus(p.getId(), WhatsappSessionStatus.CONNECTED))).toList();
        List<WppConnectClient.RemoteSession> remote = List.of();
        var active = providers.findFirstByEnabledTrueOrderByIdAsc();
        if (active.isPresent()) try { remote = client.listSessions(active.get(), crypto.decrypt(active.get().getEncryptedSecret())); }
        catch (RuntimeException ignored) { }
        return new PageData(views, connections.findAllWithDetails(), remote);
    }

    @Transactional
    public WhatsappProvider save(WhatsappProviderForm form) {
        urls.validate(form.getBaseUrl());
        WhatsappProvider provider = form.getId() == null ? new WhatsappProvider() : providers.findById(form.getId()).orElseThrow();
        provider.setName(form.getName().trim()); provider.setBaseUrl(form.getBaseUrl().trim()); provider.setEnabled(form.isEnabled());
        if (provider.getId() == null || (form.getSecret() != null && !form.getSecret().isBlank())) provider.setEncryptedSecret(crypto.encrypt(form.getSecret()));
        return providers.save(provider);
    }

    @Transactional
    public ProviderStatus test(Long providerId) {
        WhatsappProvider provider = provider(providerId);
        try {
            client.listSessions(provider, crypto.decrypt(provider.getEncryptedSecret()));
            provider.setStatus(ProviderStatus.AVAILABLE); provider.setLastError(null);
        } catch (WppConnectException ex) {
            provider.setStatus(switch (ex.getKind()) {
                case INVALID_CREDENTIALS -> ProviderStatus.INVALID_CREDENTIALS;
                case UNREACHABLE -> ProviderStatus.UNREACHABLE;
                case TIMEOUT -> ProviderStatus.TIMEOUT;
                case INCOMPATIBLE -> ProviderStatus.INCOMPATIBLE;
                default -> ProviderStatus.ERROR;
            });
            provider.setLastError(ex.getMessage());
        } catch (RuntimeException ex) {
            provider.setStatus(ProviderStatus.ERROR); provider.setLastError("Error interno al comprobar el proveedor.");
        }
        provider.setLastCheckedAt(LocalDateTime.now());
        return provider.getStatus();
    }

    @Transactional
    public void associate(Long providerId, Long tenantId, String sessionName, boolean confirmed) {
        if (!confirmed) throw new DomainException("Confirma expresamente la asociación de la sesión existente.");
        WhatsappProvider provider = provider(providerId);
        String clean = sessionName == null ? "" : sessionName.trim();
        if (clean.isBlank() || connections.existsByProviderIdAndSessionName(providerId, clean)) throw new DomainException("La sesión ya está asignada o no es válida.");
        if (connections.findByTenantIdWithDetails(tenantId).isPresent()) throw new DomainException("La tienda ya tiene una sesión asignada.");
        String secret = crypto.decrypt(provider.getEncryptedSecret());
        boolean exists = client.listSessions(provider, secret).stream().anyMatch(item -> item.name().equals(clean));
        if (!exists) throw new DomainException("La sesión no existe en este proveedor.");
        String token = client.generateSessionToken(provider, secret, clean);
        var connection = new TenantWhatsappConnection();
        connection.setTenant(tenants.findById(tenantId).orElseThrow()); connection.setProvider(provider);
        connection.setSessionName(clean); connection.setEncryptedSessionToken(crypto.encrypt(token));
        connection.setStatus(WhatsappSessionStatus.DISCONNECTED);
        connections.save(connection);
    }

    private WhatsappProvider provider(Long id) { return providers.findById(id).orElseThrow(() -> new DomainException("Proveedor no encontrado.")); }
}
