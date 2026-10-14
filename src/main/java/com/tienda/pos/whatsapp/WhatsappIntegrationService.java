package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@NormalMode
public class WhatsappIntegrationService {
    public record Dashboard(Tenant tenant, TenantWhatsappConnection connection, WhatsappProvider provider,
                            List<WhatsappGroup> groups, List<TenantIntegrationToken> tokens) { }
    public record GeneratedToken(String value, TenantIntegrationToken token) { }
    private final CurrentTenant currentTenant;
    private final WhatsappProviderRepository providers;
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappGroupRepository groups;
    private final TenantIntegrationTokenRepository tokens;
    private final WhatsappCryptoService crypto;
    private final WppConnectClient client;
    private final WhatsappProperties properties;
    private final WhatsappAnnouncementService announcements;
    private final Map<Long, Object> tenantLocks = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public WhatsappIntegrationService(CurrentTenant currentTenant, WhatsappProviderRepository providers,
                                      TenantWhatsappConnectionRepository connections, WhatsappGroupRepository groups,
                                      TenantIntegrationTokenRepository tokens, WhatsappCryptoService crypto,
                                      WppConnectClient client, WhatsappProperties properties,
                                      WhatsappAnnouncementService announcements) {
        this.currentTenant = currentTenant; this.providers = providers; this.connections = connections; this.groups = groups;
        this.tokens = tokens; this.crypto = crypto; this.client = client; this.properties = properties;
        this.announcements = announcements;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() { return dashboard(currentTenant.get()); }
    @Transactional(readOnly = true)
    public Dashboard dashboard(Tenant tenant) {
        var connection = connections.findByTenantIdWithDetails(tenant.getId()).orElse(null);
        return new Dashboard(tenant, connection, connection == null ? providers.findFirstByEnabledTrueOrderByIdAsc().orElse(null) : connection.getProvider(),
                groups.findByTenantIdAndAvailableTrueOrderByNameAsc(tenant.getId()), tokens.findByTenantIdOrderByCreatedAtDesc(tenant.getId()));
    }

    @Transactional
    public TenantWhatsappConnection connectCurrent() { return connect(currentTenant.get()); }
    public TenantWhatsappConnection connect(Tenant tenant) {
        synchronized (tenantLocks.computeIfAbsent(tenant.getId(), key -> new Object())) {
            var connection = connections.findByTenantIdWithDetails(tenant.getId()).orElse(null);
            if (connection == null) connection = createConnection(tenant);
            return start(connection, WhatsappSessionStatus.STARTING);
        }
    }

    @Transactional
    protected TenantWhatsappConnection createConnection(Tenant tenant) {
        if (!tenant.isActive()) throw new DomainException("La tienda está suspendida.");
        WhatsappProvider provider = providers.findFirstByEnabledTrueOrderByIdAsc().orElseThrow(() -> new DomainException("WPPConnect no está configurado."));
        String session = stableSessionName(tenant);
        if (connections.existsByProviderIdAndSessionName(provider.getId(), session)) throw new DomainException("El identificador de sesión ya está reservado.");
        String secret = crypto.decrypt(provider.getEncryptedSecret());
        String token = client.generateSessionToken(provider, secret, session);
        TenantWhatsappConnection connection = new TenantWhatsappConnection();
        connection.setTenant(tenant); connection.setProvider(provider); connection.setSessionName(session);
        connection.setEncryptedSessionToken(crypto.encrypt(token)); connection.setStatus(WhatsappSessionStatus.STARTING);
        return connections.save(connection);
    }

    @Transactional
    public TenantWhatsappConnection refreshCurrentStatus() { return refreshStatus(requireCurrent()); }
    @Transactional
    public TenantWhatsappConnection refreshStatus(TenantWhatsappConnection connection) {
        return refreshStatus(connection, false);
    }
    @Transactional
    public TenantWhatsappConnection refreshStatusFromMonitor(TenantWhatsappConnection connection) {
        return refreshStatus(connection, true);
    }
    private TenantWhatsappConnection refreshStatus(TenantWhatsappConnection connection, boolean updateProvider) {
        if (connection.getId() != null) {
            connection = connections.findByIdWithDetailsForUpdate(connection.getId()).orElse(connection);
        }
        try {
            var snapshot = client.status(connection.getProvider(), connection.getSessionName(), token(connection));
            applySnapshot(connection, snapshot);
            if (updateProvider) {
                connection.getProvider().setStatus(ProviderStatus.AVAILABLE);
                connection.getProvider().setLastCheckedAt(LocalDateTime.now());
                connection.getProvider().setLastError(null);
            }
        } catch (WppConnectException ex) {
            connection.setLastCheckedAt(LocalDateTime.now()); connection.setLastError(ex.getMessage());
            if (updateProvider && (ex.getKind() == WppConnectException.Kind.UNREACHABLE || ex.getKind() == WppConnectException.Kind.TIMEOUT)) {
                connection.getProvider().setStatus(ex.getKind() == WppConnectException.Kind.TIMEOUT ? ProviderStatus.TIMEOUT : ProviderStatus.UNREACHABLE);
                connection.getProvider().setLastError(ex.getMessage()); connection.getProvider().setLastCheckedAt(LocalDateTime.now());
            } else connection.setStatus(WhatsappSessionStatus.ERROR);
        }
        return connection;
    }

    @Transactional
    public TenantWhatsappConnection reconnectCurrent() { return start(requireCurrent(), WhatsappSessionStatus.RECONNECTING); }
    @Transactional
    protected TenantWhatsappConnection start(TenantWhatsappConnection connection, WhatsappSessionStatus interim) {
        connection.setStatus(interim); connection.setLastError(null);
        try { applySnapshot(connection, client.start(connection.getProvider(), connection.getSessionName(), token(connection))); }
        catch (WppConnectException ex) { connection.setStatus(WhatsappSessionStatus.ERROR); connection.setLastError(ex.getMessage()); throw ex; }
        return connection;
    }

    @Transactional(readOnly = true)
    public byte[] qrCurrent() {
        TenantWhatsappConnection connection = requireCurrent();
        return client.qrCode(connection.getProvider(), connection.getSessionName(), token(connection));
    }

    @Transactional
    public List<WhatsappGroup> refreshCurrentGroups() { return refreshGroups(requireCurrent()); }
    @Transactional
    public List<WhatsappGroup> refreshGroups(TenantWhatsappConnection connection) {
        if (connection.getId() != null) connection = connections.findByIdWithDetails(connection.getId()).orElse(connection);
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED) refreshStatus(connection);
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED) throw new DomainException("Conecta WhatsApp para consultar sus grupos.");
        List<WppConnectClient.RemoteGroup> remote = client.groups(connection.getProvider(), connection.getSessionName(), token(connection));
        LocalDateTime now = LocalDateTime.now();
        Map<String, WhatsappGroup> stored = new HashMap<>();
        for (WhatsappGroup group : groups.findByTenantId(connection.getTenant().getId())) { group.setAvailable(false); stored.put(group.getWhatsappId(), group); }
        for (var item : remote) {
            WhatsappGroup group = stored.getOrDefault(item.id(), new WhatsappGroup());
            group.setTenant(connection.getTenant()); group.setConnection(connection); group.setWhatsappId(item.id());
            group.setName(item.name()); group.setParticipantCount(item.participants()); group.setAvailable(true); group.setRefreshedAt(now);
            groups.save(group);
        }
        return groups.findByTenantIdAndAvailableTrueOrderByNameAsc(connection.getTenant().getId());
    }

    @Transactional
    public GeneratedToken generateToken(String name, Set<IntegrationScope> scopes) {
        Tenant tenant = currentTenant.get();
        if (scopes == null || scopes.isEmpty()) throw new DomainException("Selecciona al menos un permiso.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String value = "tip_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TenantIntegrationToken token = new TenantIntegrationToken(); token.setTenant(tenant);
        token.setName(name == null || name.isBlank() ? "n8n" : name.trim()); token.setTokenPrefix(value.substring(0, 12));
        token.setTokenHash(crypto.hashToken(value)); token.setScopes(scopes.stream().map(Enum::name).sorted().reduce((a,b) -> a + "," + b).orElseThrow());
        return new GeneratedToken(value, tokens.save(token));
    }

    @Transactional
    public void revokeCurrentToken(Long id) {
        TenantIntegrationToken token = tokens.findByIdAndTenantId(id, currentTenant.id()).orElseThrow(() -> new DomainException("Credencial no encontrada."));
        token.setActive(false); token.setRevokedAt(LocalDateTime.now());
    }

    @Transactional
    public void disassociateCurrent() {
        Long tenantId = currentTenant.id();
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenantId)
                .orElseThrow(() -> new DomainException("WhatsApp no está configurado."));
        announcements.invalidateForDisassociation(tenantId);
        groups.deleteByTenantId(tenantId);
        connections.delete(connection);
    }

    @Transactional(readOnly = true)
    public WppConnectClient.SendResult sendText(Tenant tenant, String phone, boolean isGroup, String message) {
        if (message == null || message.isBlank() || message.length() > 10000) throw new DomainException("El mensaje es obligatorio y no puede superar 10000 caracteres.");
        TenantWhatsappConnection connection = ready(tenant, phone, isGroup);
        return client.sendText(connection.getProvider(), connection.getSessionName(), token(connection), normalizeDestination(phone, isGroup), isGroup, message.trim());
    }

    @Transactional(readOnly = true)
    public WppConnectClient.SendResult sendFile(Tenant tenant, String phone, boolean isGroup, MultipartFile file, String caption) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > properties.getMaxUploadBytes()) throw new DomainException("El archivo es obligatorio y no debe superar 10 MB.");
            byte[] bytes = file.getBytes(); String type = detectType(bytes);
            if (type == null) throw new DomainException("Solo se permiten imágenes PNG/JPEG/WebP o documentos PDF válidos.");
            TenantWhatsappConnection connection = ready(tenant, phone, isGroup);
            String filename = safeFilename(file.getOriginalFilename(), type);
            return client.sendFile(connection.getProvider(), connection.getSessionName(), token(connection), normalizeDestination(phone, isGroup), isGroup, filename, type, bytes, caption);
        } catch (java.io.IOException ex) { throw new DomainException("No fue posible leer el archivo."); }
    }

    private TenantWhatsappConnection ready(Tenant tenant, String phone, boolean group) {
        if (!tenant.isActive()) throw new DomainException("La tienda está suspendida.");
        TenantWhatsappConnection connection = connections.findByTenantIdWithDetails(tenant.getId()).orElseThrow(() -> new DomainException("WhatsApp no está configurado."));
        if (connection.getStatus() != WhatsappSessionStatus.CONNECTED) throw new DomainException("WhatsApp no está conectado.");
        String destination = normalizeDestination(phone, group);
        if (group && groups.findByTenantIdAndWhatsappId(tenant.getId(), destination).filter(WhatsappGroup::isAvailable).isEmpty()) throw new DomainException("El grupo no pertenece a esta sesión.");
        return connection;
    }

    private String normalizeDestination(String value, boolean group) {
        String clean = value == null ? "" : value.trim();
        if (group) { if (!clean.matches("[0-9-]{5,80}@g\\.us")) throw new DomainException("El identificador de grupo no es válido."); return clean; }
        clean = clean.replaceAll("[^0-9]", "");
        if (!clean.matches("[0-9]{8,20}")) throw new DomainException("El número de WhatsApp no es válido.");
        return clean;
    }
    private String detectType(byte[] value) {
        if (value.length >= 5 && new String(value, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        if (value.length >= 8 && value[0] == (byte)0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47) return "image/png";
        if (value.length >= 3 && value[0] == (byte)0xff && value[1] == (byte)0xd8 && value[2] == (byte)0xff) return "image/jpeg";
        if (value.length >= 12 && new String(value,0,4).equals("RIFF") && new String(value,8,4).equals("WEBP")) return "image/webp";
        return null;
    }
    private String safeFilename(String value, String type) {
        String extension = type.equals("application/pdf") ? ".pdf" : type.endsWith("png") ? ".png" : type.endsWith("webp") ? ".webp" : ".jpg";
        String fallback = type.equals("application/pdf") ? "documento.pdf" : "imagen" + extension;
        if (value == null || value.isBlank()) return fallback;
        String clean = value.replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("\\.[^.]+$", "") + extension;
        return clean.length() > 120 ? clean.substring(clean.length()-120) : clean;
    }
    private void applySnapshot(TenantWhatsappConnection connection, WppConnectClient.SessionSnapshot snapshot) {
        connection.setStatus(snapshot.status());
        if (snapshot.phoneNumber() != null && !snapshot.phoneNumber().isBlank()) {
            connection.setPhoneNumber(snapshot.phoneNumber());
        }
        connection.setLastCheckedAt(LocalDateTime.now()); connection.setLastError(null);
        if (snapshot.status() == WhatsappSessionStatus.CONNECTED) connection.setLastConnectedAt(LocalDateTime.now());
        announcements.invalidateIfIdentityChanged(connection);
    }
    private TenantWhatsappConnection requireCurrent() { return connections.findByTenantIdWithDetails(currentTenant.id()).orElseThrow(() -> new DomainException("WhatsApp no está configurado.")); }
    private String token(TenantWhatsappConnection connection) { return crypto.decrypt(connection.getEncryptedSessionToken()); }
    private String stableSessionName(Tenant tenant) {
        String code = tenant.getCode().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return ("tp_" + tenant.getId() + "_" + code).substring(0, Math.min(100, ("tp_" + tenant.getId() + "_" + code).length()));
    }
}
